package com.sparrowwallet.sparrow.io;

import com.csvreader.CsvReader;
import com.google.gson.*;
import com.sparrowwallet.drongo.KeyDerivation;
import com.sparrowwallet.drongo.KeyPurpose;
import com.sparrowwallet.drongo.OutputDescriptor;
import com.sparrowwallet.drongo.Utils;
import com.sparrowwallet.drongo.protocol.ScriptType;
import com.sparrowwallet.drongo.protocol.Sha256Hash;
import com.sparrowwallet.drongo.protocol.Transaction;
import com.sparrowwallet.drongo.wallet.*;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.event.KeystoreLabelsChangedEvent;
import com.sparrowwallet.sparrow.event.WalletEntryLabelsChangedEvent;
import com.sparrowwallet.sparrow.event.WalletUtxoStatusChangedEvent;
import com.sparrowwallet.sparrow.net.ExchangeSource;
import com.sparrowwallet.sparrow.wallet.*;
import org.apache.commons.lang3.time.DateUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class WalletLabels implements WalletImport, WalletExport {
    private static final Logger log = LoggerFactory.getLogger(WalletLabels.class);
    private static final long ONE_DAY = 24*60*60*1000L;

    private final List<WalletForm> walletForms;

    public WalletLabels(List<WalletForm> walletForms) {
        this.walletForms = walletForms;
    }

    @Override
    public boolean isEncrypted(File file) {
        return false;
    }

    @Override
    public String getName() {
        return "Labels";
    }

    @Override
    public WalletModel getWalletModel() {
        return WalletModel.LABELS;
    }

    @Override
    public void exportWallet(Wallet wallet, OutputStream outputStream, String password) throws ExportException {
        List<Label> labels = new ArrayList<>();
        Map<Date, Double> fiatRates = getFiatRates(walletForms);
        for(WalletForm exportWalletForm : walletForms) {
            Wallet exportWallet = exportWalletForm.getWallet();
            OutputDescriptor outputDescriptor = OutputDescriptor.getOutputDescriptor(exportWallet);
            String origin = outputDescriptor.toString(true, false, false);

            for(Keystore keystore : exportWallet.getKeystores()) {
                if(keystore.getLabel() != null && !keystore.getLabel().isEmpty()) {
                    labels.add(new Label(Type.xpub, keystore.getExtendedPublicKey().toString(), keystore.getLabel(), null, null));
                }
            }

            Set<Sha256Hash> confirmingTxs = new HashSet<>();
            WalletTransactionsEntry walletTransactionsEntry = exportWalletForm.getWalletTransactionsEntry();
            for(Entry entry : walletTransactionsEntry.getChildren()) {
                TransactionEntry txEntry = (TransactionEntry)entry;
                BlockTransaction blkTx = txEntry.getBlockTransaction();
                labels.add(new TransactionLabel(blkTx.getHashAsString(), blkTx.getLabel(), origin,
                        txEntry.isConfirming() ? null : blkTx.getHeight(), blkTx.getDate(),
                        blkTx.getFee() == null || blkTx.getFee() == 0 ? null : blkTx.getFee(), txEntry.getValue(),
                        getFiatValue(blkTx.getDate(), Transaction.SATOSHIS_PER_BITCOIN, fiatRates)));
                if(txEntry.isConfirming()) {
                    confirmingTxs.add(blkTx.getHash());
                }
            }

            for(WalletNode addressNode : exportWallet.getWalletAddresses().values()) {
                labels.add(new AddressLabel(addressNode.getAddress().toString(), addressNode.getLabel(), origin, addressNode.getDerivationPath().substring(1),
                        addressNode.getTransactionOutputs().stream().flatMap(txo -> txo.isSpent() ? Stream.of(txo, txo.getSpentBy()) : Stream.of(txo))
                                .filter(ref -> !confirmingTxs.contains(ref.getHash())).map(BlockTransactionHash::getHeight).toList()));
            }

            for(Map.Entry<BlockTransactionHashIndex, WalletNode> txoEntry : exportWallet.getWalletTxos().entrySet()) {
                BlockTransactionHashIndex txo = txoEntry.getKey();
                WalletNode addressNode = txoEntry.getValue();
                Boolean spendable = (txo.isSpent() ? null : txo.getStatus() != Status.FROZEN);
                labels.add(new InputOutputLabel(Type.output, txo.toString(), txo.getLabel(), origin, spendable, addressNode.getDerivationPath().substring(1), txo.getValue(),
                        confirmingTxs.contains(txo.getHash()) ? null : txo.getHeight(), txo.getDate(), getFiatValue(txo, fiatRates)));

                if(txo.isSpent()) {
                    BlockTransactionHashIndex txi = txo.getSpentBy();
                    labels.add(new InputOutputLabel(Type.input, txi.toString(), txi.getLabel(), origin, null, addressNode.getDerivationPath().substring(1), txi.getValue(),
                            confirmingTxs.contains(txi.getHash()) ? null : txi.getHeight(), txi.getDate(), getFiatValue(txi, fiatRates)));
                }
            }
        }

        try {
            Gson gson = new GsonBuilder().registerTypeAdapter(Date.class, new GsonUTCDateAdapter()).create();
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8));

            for(Label label : labels) {
                writer.write(gson.toJson(label) + "\n");
            }

            writer.flush();
        } catch(Exception e) {
            log.error("Error exporting labels", e);
            throw new ExportException("Error exporting labels", e);
        }
    }

    @Override
    public String getWalletExportDescription() {
        return "Exports a file containing labels from this wallet in the BIP329 standard format.";
    }

    @Override
    public String getExportFileExtension(Wallet wallet) {
        return "jsonl";
    }

    @Override
    public boolean isWalletExportScannable() {
        return true;
    }

    @Override
    public boolean walletExportRequiresDecryption() {
        return false;
    }

    @Override
    public String getWalletImportDescription() {
        return "Imports a file containing labels in the BIP329 standard (or Electrum history CSV) format to the currently selected wallet.";
    }

    @Override
    public Wallet importWallet(InputStream inputStream, String password) throws ImportException {
        if(walletForms.isEmpty()) {
            throw new IllegalStateException("No wallets to import labels for");
        }

        Gson gson = new Gson();
        List<Label> labels = new ArrayList<>();
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
            String line;
            while((line = reader.readLine()) != null) {
                Label label;
                try {
                    label = gson.fromJson(line, Label.class);
                } catch(Exception e) {
                    //Try parse Electrum history CSV, or any CSV with txid,label entries
                    try {
                        CsvReader csvReader = new CsvReader(new StringReader(line));
                        if(csvReader.readRecord() && csvReader.getColumnCount() > 1 && csvReader.get(0).length() == 64 && Utils.isHex(csvReader.get(0))) {
                            label = new Label(Type.tx, csvReader.get(0), csvReader.get(1), null, null);
                        } else {
                            continue;
                        }
                    } catch(Exception ex) {
                        continue;
                    }
                }

                if(label == null || label.type == null || label.ref == null) {
                    continue;
                }

                if(label.type == Type.output) {
                    if((label.label == null || label.label.isEmpty()) && label.spendable == null) {
                        continue;
                    }
                } else if(label.label == null || label.label.isEmpty()) {
                    continue;
                }

                labels.add(label);
            }
        } catch(Exception e) {
            throw new ImportException("Error importing labels file", e);
        }

        Map<Wallet, List<Keystore>> changedWalletKeystores = new LinkedHashMap<>();
        Map<Wallet, List<Entry>> changedWalletEntries = new LinkedHashMap<>();
        Map<Wallet, List<BlockTransactionHashIndex>> changedWalletUtxoStatuses = new LinkedHashMap<>();

        for(WalletForm walletForm : walletForms) {
            Wallet wallet = walletForm.getWallet();
            if(!wallet.isValid()) {
                continue;
            }

            OutputDescriptor outputDescriptor = OutputDescriptor.getOutputDescriptor(wallet);
            Origin origin = Origin.fromOutputDescriptor(outputDescriptor);

            List<Entry> transactionEntries = walletForm.getWalletTransactionsEntry().getChildren();
            List<Entry> addressEntries = new ArrayList<>();
            addressEntries.addAll(walletForm.getNodeEntry(KeyPurpose.RECEIVE).getChildren());
            addressEntries.addAll(walletForm.getNodeEntry(KeyPurpose.CHANGE).getChildren());
            List<Entry> utxoEntries = walletForm.getWalletUtxosEntry().getChildren();

            for(Label label : labels) {
                if(label.origin != null && !Origin.fromString(label.origin).equals(origin)) {
                    continue;
                }

                if(label.type == Type.xpub) {
                    for(Keystore keystore : wallet.getKeystores()) {
                        if(keystore.getExtendedPublicKey().toString().equals(label.ref)) {
                            keystore.setLabel(label.label);
                            List<Keystore> changedKeystores = changedWalletKeystores.computeIfAbsent(wallet, w -> new ArrayList<>());
                            changedKeystores.add(keystore);
                        }
                    }
                }

                if(label.type == Type.tx) {
                    for(Entry entry : transactionEntries) {
                        if(entry instanceof TransactionEntry transactionEntry) {
                            BlockTransaction blkTx = transactionEntry.getBlockTransaction();
                            if(blkTx.getHashAsString().equals(label.ref)) {
                                transactionEntry.getBlockTransaction().setLabel(label.label);
                                transactionEntry.labelProperty().set(label.label);
                                addChangedEntry(changedWalletEntries, entry);
                            }
                        }
                    }
                }

                if(label.type == Type.addr) {
                    for(Entry addressEntry : addressEntries) {
                        if(addressEntry instanceof NodeEntry nodeEntry) {
                            WalletNode addressNode = nodeEntry.getNode();
                            if(addressNode.getAddress().toString().equals(label.ref)) {
                                nodeEntry.getNode().setLabel(label.label);
                                nodeEntry.labelProperty().set(label.label);
                                addChangedEntry(changedWalletEntries, addressEntry);
                            }
                        }
                    }
                }

                if(label.type == Type.output || label.type == Type.input) {
                    for(Entry entry : transactionEntries) {
                        for(Entry hashIndexEntry : entry.getChildren()) {
                            if(hashIndexEntry instanceof TransactionHashIndexEntry txioEntry) {
                                BlockTransactionHashIndex reference = txioEntry.getHashIndex();
                                if((label.type == Type.output && txioEntry.getType() == HashIndexEntry.Type.OUTPUT && reference.toString().equals(label.ref))
                                        || (label.type == Type.input && txioEntry.getType() == HashIndexEntry.Type.INPUT && reference.toString().equals(label.ref))) {
                                    if(label.label != null && !label.label.isEmpty()) {
                                        reference.setLabel(label.label);
                                        txioEntry.labelProperty().set(label.label);
                                        addChangedEntry(changedWalletEntries, txioEntry);
                                    }

                                    if(label.type == Type.output && !reference.isSpent() && label.spendable != null) {
                                        if(!label.spendable && reference.getStatus() != Status.FROZEN) {
                                            reference.setStatus(Status.FROZEN);
                                            addChangedUtxo(changedWalletUtxoStatuses, txioEntry);
                                        } else if(label.spendable && reference.getStatus() == Status.FROZEN) {
                                            reference.setStatus(null);
                                            addChangedUtxo(changedWalletUtxoStatuses, txioEntry);
                                        }
                                    }
                                }
                            }
                        }
                    }
                    for(Entry addressEntry : addressEntries) {
                        for(Entry entry : addressEntry.getChildren()) {
                            updateHashIndexEntryLabel(label, entry);
                            for(Entry spentEntry : entry.getChildren()) {
                                updateHashIndexEntryLabel(label, spentEntry);
                            }
                        }
                    }
                    for(Entry entry : utxoEntries) {
                        updateHashIndexEntryLabel(label, entry);
                    }
                }
            }
        }

        for(Map.Entry<Wallet, List<Keystore>> walletKeystores : changedWalletKeystores.entrySet()) {
            Wallet wallet = walletKeystores.getKey();
            Storage storage = AppServices.get().getOpenWallets().get(wallet);
            EventManager.get().post(new KeystoreLabelsChangedEvent(wallet, wallet, storage.getWalletId(wallet), walletKeystores.getValue()));
        }

        for(Map.Entry<Wallet, List<Entry>> walletEntries : changedWalletEntries.entrySet()) {
            EventManager.get().post(new WalletEntryLabelsChangedEvent(walletEntries.getKey(), walletEntries.getValue(), false));
        }

        for(Map.Entry<Wallet, List<BlockTransactionHashIndex>> walletUtxos : changedWalletUtxoStatuses.entrySet()) {
            EventManager.get().post(new WalletUtxoStatusChangedEvent(walletUtxos.getKey(), walletUtxos.getValue()));
        }

        return walletForms.get(0).getWallet();
    }

    private static void updateHashIndexEntryLabel(Label label, Entry entry) {
        if(entry instanceof HashIndexEntry hashIndexEntry) {
            BlockTransactionHashIndex reference = hashIndexEntry.getHashIndex();
            if((label.type == Type.output && hashIndexEntry.getType() == HashIndexEntry.Type.OUTPUT && reference.toString().equals(label.ref))
                    || (label.type == Type.input && hashIndexEntry.getType() == HashIndexEntry.Type.INPUT && reference.toString().equals(label.ref))) {
                if(label.label != null && !label.label.isEmpty()) {
                    hashIndexEntry.labelProperty().set(label.label);
                }
            }
        }
    }

    private static void addChangedEntry(Map<Wallet, List<Entry>> changedEntries, Entry entry) {
        List<Entry> entries = changedEntries.computeIfAbsent(entry.getWallet(), wallet -> new ArrayList<>());
        entries.add(entry);
    }

    private static void addChangedUtxo(Map<Wallet, List<BlockTransactionHashIndex>> changedUtxos, TransactionHashIndexEntry utxoEntry) {
        List<BlockTransactionHashIndex> utxos = changedUtxos.computeIfAbsent(utxoEntry.getWallet(), w -> new ArrayList<>());
        utxos.add(utxoEntry.getHashIndex());
    }

    @Override
    public boolean isWalletImportScannable() {
        return true;
    }

    @Override
    public boolean exportsAllWallets() {
        return true;
    }

    private Map<Date, Double> getFiatRates(List<WalletForm> walletForms) {
        ExchangeSource exchangeSource = getExchangeSource();
        Currency fiatCurrency = getFiatCurrency();
        Map<Date, Double> fiatRates = new HashMap<>();
        if(fiatCurrency != null) {
            long min = Long.MAX_VALUE;
            long max = Long.MIN_VALUE;

            for(WalletForm walletForm : walletForms) {
                WalletTransactionsEntry walletTransactionsEntry = walletForm.getWalletTransactionsEntry();
                if(!walletTransactionsEntry.getChildren().isEmpty()) {
                    LongSummaryStatistics stats = walletTransactionsEntry.getChildren().stream()
                            .map(entry -> ((TransactionEntry)entry).getBlockTransaction().getDate())
                            .filter(Objects::nonNull)
                            .collect(Collectors.summarizingLong(Date::getTime));
                    min = Math.min(min, stats.getMin());
                    max = Math.max(max, stats.getMax());
                }
            }

            if(max > min) {
                fiatRates = exchangeSource.getHistoricalExchangeRates(fiatCurrency, new Date(min - ONE_DAY), new Date(max));
            }
        }

        return fiatRates;
    }

    private static ExchangeSource getExchangeSource() {
        return Config.get().getExchangeSource() == null ? ExchangeSource.COINGECKO : Config.get().getExchangeSource();
    }

    private static Currency getFiatCurrency() {
        return getExchangeSource() == ExchangeSource.NONE || !AppServices.onlineProperty().get() ? null : Config.get().getFiatCurrency();
    }

    private Map<Currency, BigDecimal> getFiatValue(TransactionEntry txEntry, Map<Date, Double> fiatRates) {
        return getFiatValue(txEntry.getBlockTransaction().getDate(), txEntry.getValue(), fiatRates);
    }

    private Map<Currency, BigDecimal> getFiatValue(BlockTransactionHashIndex ref, Map<Date, Double> fiatRates) {
        return getFiatValue(ref.getDate(), ref.getValue(), fiatRates);
    }

    private Map<Currency, BigDecimal> getFiatValue(Date date, long value, Map<Date, Double> fiatRates) {
        Currency fiatCurrency = getFiatCurrency();
        if(fiatCurrency != null) {
            Double dayRate = null;
            if(date == null) {
                if(AppServices.getFiatCurrencyExchangeRate() != null) {
                    dayRate = AppServices.getFiatCurrencyExchangeRate().getBtcRate();
                }
            } else {
                dayRate = fiatRates.get(DateUtils.truncate(date, Calendar.DAY_OF_MONTH));
            }

            if(dayRate != null) {
                BigDecimal fiatValue = BigDecimal.valueOf(dayRate * value / Transaction.SATOSHIS_PER_BITCOIN);
                return Map.of(fiatCurrency, fiatValue.setScale(fiatCurrency.getDefaultFractionDigits(), RoundingMode.HALF_UP));
            }
        }

        return null;
    }

    private enum Type {
        tx, addr, pubkey, input, output, xpub
    }

    private static class Label {
        public Label(Type type, String ref, String label, String origin, Boolean spendable) {
            this.type = type;
            this.ref = ref;
            this.label = label;
            this.origin = origin;
            this.spendable = spendable;
        }

        Type type;
        String ref;
        String label;
        String origin;
        Boolean spendable;
    }

    private static class TransactionLabel extends Label {
        public TransactionLabel(String ref, String label, String origin, Integer height, Date time, Long fee, Long value, Map<Currency, BigDecimal> rate) {
            super(Type.tx, ref, label, origin, null);
            this.height = height;
            this.time = time;
            this.fee = fee;
            this.value = value;
            this.rate = rate;
        }

        Integer height;
        Date time;
        Long fee;
        Long value;
        Map<Currency, BigDecimal> rate;
    }

    private static class AddressLabel extends Label {
        public AddressLabel(String ref, String label, String origin, String keypath, List<Integer> heights) {
            super(Type.addr, ref, label, origin, null);
            this.keypath = keypath;
            this.heights = heights;
        }

        String keypath;
        List<Integer> heights;
    }

    private static class InputOutputLabel extends Label {
        public InputOutputLabel(Type type, String ref, String label, String origin, Boolean spendable, String keypath, Long value, Integer height, Date time, Map<Currency, BigDecimal> fmv) {
            super(type, ref, label, origin, spendable);
            this.keypath = keypath;
            this.value = value;
            this.height = height;
            this.time = time;
            this.fmv = fmv;
        }

        String keypath;
        Long value;
        Integer height;
        Date time;
        Map<Currency, BigDecimal> fmv;
    }

    public static class GsonUTCDateAdapter implements JsonSerializer<Date>, JsonDeserializer<Date> {
        private final DateFormat dateFormat;

        public GsonUTCDateAdapter() {
            dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
            dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        }

        @Override
        public JsonElement serialize(Date src, java.lang.reflect.Type typeOfSrc, JsonSerializationContext context) {
            return new JsonPrimitive(dateFormat.format(src));
        }

        @Override
        public Date deserialize(JsonElement json, java.lang.reflect.Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            try {
                return dateFormat.parse(json.getAsString());
            } catch (ParseException e) {
                throw new JsonParseException(e);
            }
        }
    }

    private static class Origin {
        private static final Pattern KEY_ORIGIN_PATTERN = Pattern.compile("\\[([A-Fa-f0-9]{8})([/\\d'hH]+)?\\]");

        private ScriptType scriptType;
        private Set<KeyDerivation> keyDerivations;

        @Override
        public final boolean equals(Object o) {
            if(this == o) {
                return true;
            }
            if(!(o instanceof Origin origin)) {
                return false;
            }

            return scriptType == origin.scriptType && keyDerivations.equals(origin.keyDerivations);
        }

        @Override
        public int hashCode() {
            int result = Objects.hashCode(scriptType);
            result = 31 * result + keyDerivations.hashCode();
            return result;
        }

        public static Origin fromOutputDescriptor(OutputDescriptor outputDescriptor) {
            Origin origin = new Origin();
            origin.scriptType = outputDescriptor.getScriptType();
            origin.keyDerivations = new HashSet<>(outputDescriptor.getExtendedPublicKeysMap().values());
            return origin;
        }

        public static Origin fromString(String strOrigin) {
            Origin origin = new Origin();
            origin.scriptType = ScriptType.fromDescriptor(strOrigin);
            origin.keyDerivations = new HashSet<>();
            Matcher keyOriginMatcher = KEY_ORIGIN_PATTERN.matcher(strOrigin);
            while(keyOriginMatcher.find()) {
                byte[] masterFingerprintBytes = keyOriginMatcher.group(1) != null ? Utils.hexToBytes(keyOriginMatcher.group(1)) : new byte[4];
                origin.keyDerivations.add(new KeyDerivation(Utils.bytesToHex(masterFingerprintBytes), KeyDerivation.writePath(KeyDerivation.parsePath(keyOriginMatcher.group(2)))));
            }
            return origin;
        }
    }
}
