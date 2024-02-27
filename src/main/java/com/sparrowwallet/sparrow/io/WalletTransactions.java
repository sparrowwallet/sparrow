package com.sparrowwallet.sparrow.io;

import com.csvreader.CsvWriter;
import com.sparrowwallet.drongo.BitcoinUnit;
import com.sparrowwallet.drongo.protocol.Transaction;
import com.sparrowwallet.drongo.protocol.TransactionInput;
import com.sparrowwallet.drongo.protocol.TransactionOutput;
import com.sparrowwallet.drongo.wallet.BlockTransaction;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.drongo.wallet.WalletModel;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.UnitFormat;
import com.sparrowwallet.sparrow.net.ExchangeSource;
import com.sparrowwallet.sparrow.wallet.Entry;
import com.sparrowwallet.sparrow.wallet.TransactionEntry;
import com.sparrowwallet.sparrow.wallet.WalletForm;
import com.sparrowwallet.sparrow.wallet.WalletTransactionsEntry;
import org.apache.commons.lang3.time.DateUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

public class WalletTransactions implements WalletExport {
    private static final long ONE_DAY = 24*60*60*1000L;
    public static final DateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private final WalletForm walletForm;

    public WalletTransactions(WalletForm walletForm) {
        this.walletForm = walletForm;
    }

    @Override
    public String getName() {
        return "Transactions";
    }

    @Override
    public WalletModel getWalletModel() {
        return WalletModel.TRANSACTIONS;
    }

    @Override
    public void exportWallet(Wallet wallet, OutputStream outputStream, String password) throws ExportException {
        WalletTransactionsEntry walletTransactionsEntry = walletForm.getWalletTransactionsEntry();

        ExchangeSource exchangeSource = Config.get().getExchangeSource();
        if(Config.get().getExchangeSource() == null) {
            exchangeSource = ExchangeSource.COINGECKO;
        }

        Currency fiatCurrency = (exchangeSource == ExchangeSource.NONE || !AppServices.onlineProperty().get() ? null : Config.get().getFiatCurrency());
        Map<Date, Double> fiatRates = new HashMap<>();
        if(fiatCurrency != null && !walletTransactionsEntry.getChildren().isEmpty()) {
            LongSummaryStatistics stats = walletTransactionsEntry.getChildren().stream()
                    .map(entry -> ((TransactionEntry)entry).getBlockTransaction().getDate())
                    .filter(Objects::nonNull)
                    .collect(Collectors.summarizingLong(Date::getTime));
            fiatRates = exchangeSource.getHistoricalExchangeRates(fiatCurrency, new Date(stats.getMin() - ONE_DAY), new Date(stats.getMax()));
        }

        BitcoinUnit bitcoinUnit = Config.get().getBitcoinUnit();
        if(bitcoinUnit == null || bitcoinUnit.equals(BitcoinUnit.AUTO)) {
            bitcoinUnit = walletForm.getWallet().getAutoUnit();
        }

        try {
            CsvWriter writer = new CsvWriter(outputStream, ',', StandardCharsets.UTF_8);

            writer.write("Date");
            writer.write("Label");
            writer.write("Value");
            writer.write("Balance");
            writer.write("Fee");
            if(fiatCurrency != null) {
                writer.write("Value (" + fiatCurrency.getCurrencyCode() + ")");
            }
            writer.write("Txid");
            writer.endRecord();

            for(Entry entry : walletTransactionsEntry.getChildren()) {
                TransactionEntry txEntry = (TransactionEntry)entry;
                writer.write(txEntry.getBlockTransaction().getDate() == null ? "Unconfirmed" : DATE_FORMAT.format(txEntry.getBlockTransaction().getDate()));
                writer.write(txEntry.getLabel());
                writer.write(getCoinValue(bitcoinUnit, txEntry.getValue()));
                writer.write(getCoinValue(bitcoinUnit, txEntry.getBalance()));
                Long fee = txEntry.getValue() < 0 ? getFee(wallet, txEntry.getBlockTransaction()) : null;
                writer.write(fee == null ? "" : getCoinValue(bitcoinUnit, fee));
                if(fiatCurrency != null) {
                    Double fiatValue = getFiatValue(txEntry, fiatRates);
                    writer.write(fiatValue == null ? "" : getFiatValue(fiatValue));
                }
                writer.write(txEntry.getBlockTransaction().getHash().toString());
                writer.endRecord();
            }

            if(fiatCurrency != null) {
                writer.endRecord();
                writer.writeComment(" Historical " + fiatCurrency.getCurrencyCode() + " values are taken from daily rates and should only be considered as approximate.");
            }

            writer.close();
        } catch(IOException e) {
            throw new ExportException("Error writing transactions CSV", e);
        }
    }

    private Long getFee(Wallet wallet, BlockTransaction blockTransaction) {
        long fee = 0L;
        for(TransactionInput txInput : blockTransaction.getTransaction().getInputs()) {
            if(txInput.isCoinBase()) {
                return 0L;
            }

            BlockTransaction inputTx = wallet.getWalletTransaction(txInput.getOutpoint().getHash());
            if(inputTx == null || inputTx.getTransaction().getOutputs().size() <= txInput.getOutpoint().getIndex()) {
                return null;
            }
            TransactionOutput spentOutput = inputTx.getTransaction().getOutputs().get((int)txInput.getOutpoint().getIndex());
            fee += spentOutput.getValue();
        }

        for(TransactionOutput txOutput : blockTransaction.getTransaction().getOutputs()) {
            fee -= txOutput.getValue();
        }

        return fee;
    }

    private String getCoinValue(BitcoinUnit bitcoinUnit, Long value) {
        UnitFormat format = Config.get().getUnitFormat() == null ? UnitFormat.DOT : Config.get().getUnitFormat();
        return BitcoinUnit.BTC.equals(bitcoinUnit) ? format.tableFormatBtcValue(value) : String.format(Locale.ENGLISH, "%d", value);
    }

    private String getFiatValue(Double value) {
        UnitFormat format = Config.get().getUnitFormat() == null ? UnitFormat.DOT : Config.get().getUnitFormat();
        return format.tableFormatCurrencyValue(value);
    }

    private Double getFiatValue(TransactionEntry txEntry, Map<Date, Double> fiatRates) {
        Double dayRate = null;
        if(txEntry.getBlockTransaction().getDate() == null) {
            if(AppServices.getFiatCurrencyExchangeRate() != null) {
                dayRate = AppServices.getFiatCurrencyExchangeRate().getBtcRate();
            }
        } else {
            dayRate = fiatRates.get(DateUtils.truncate(txEntry.getBlockTransaction().getDate(), Calendar.DAY_OF_MONTH));
        }

        if(dayRate != null) {
            return dayRate * txEntry.getValue() / Transaction.SATOSHIS_PER_BITCOIN;
        }

        return null;
    }

    @Override
    public String getWalletExportDescription() {
        return "Exports a CSV file containing dates and values of the transactions in this wallet. Additionally, if a fiat currency has been configured, the fiat value on the day they were first included in the blockchain.";
    }

    @Override
    public String getExportFileExtension(Wallet wallet) {
        return "csv";
    }

    @Override
    public boolean isWalletExportScannable() {
        return false;
    }

    @Override
    public boolean walletExportRequiresDecryption() {
        return false;
    }
}
