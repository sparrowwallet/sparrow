package com.sparrowwallet.sparrow.timelockrecovery;

import com.sparrowwallet.drongo.KeyPurpose;
import com.sparrowwallet.drongo.Network;
import com.sparrowwallet.drongo.Utils;
import com.sparrowwallet.drongo.address.Address;
import com.sparrowwallet.drongo.crypto.ECKey;
import com.sparrowwallet.drongo.policy.PolicyType;
import com.sparrowwallet.drongo.protocol.Script;
import com.sparrowwallet.drongo.protocol.ScriptType;
import com.sparrowwallet.drongo.protocol.Sha256Hash;
import com.sparrowwallet.drongo.protocol.Transaction;
import com.sparrowwallet.drongo.protocol.TransactionInput;
import com.sparrowwallet.drongo.protocol.TransactionOutput;
import com.sparrowwallet.drongo.psbt.PSBT;
import com.sparrowwallet.drongo.psbt.PSBTInput;
import com.sparrowwallet.drongo.psbt.PSBTOutput;
import com.sparrowwallet.drongo.psbt.PSBTProofException;
import com.sparrowwallet.drongo.wallet.BlockTransactionHashIndex;
import com.sparrowwallet.drongo.wallet.CoinbaseTxoFilter;
import com.sparrowwallet.drongo.wallet.FrozenTxoFilter;
import com.sparrowwallet.drongo.wallet.InsufficientFundsException;
import com.sparrowwallet.drongo.wallet.Keystore;
import com.sparrowwallet.drongo.wallet.MaxUtxoSelector;
import com.sparrowwallet.drongo.wallet.Payment;
import com.sparrowwallet.drongo.wallet.SpentTxoFilter;
import com.sparrowwallet.drongo.wallet.Status;
import com.sparrowwallet.drongo.wallet.TransactionParameters;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.drongo.wallet.WalletNode;
import com.sparrowwallet.drongo.wallet.WalletNodePayment;
import com.sparrowwallet.drongo.wallet.WalletTransaction;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class TimelockRecovery {
    public static final long ANCHOR_AMOUNT_SATS = 600L;
    public static final int MIN_TIMELOCK_DAYS = 2;
    public static final int MAX_TIMELOCK_DAYS = 388;
    public static final int DEFAULT_TIMELOCK_DAYS = 90;
    public static final String INITIATION_ADDRESS_LABEL = "Timelock Recovery Initiation Address";
    public static final String CANCELLATION_ADDRESS_LABEL = "Timelock Recovery Cancellation Address";
    public static final String SITE_URL = "https://timelockrecovery.com";
    public static final int QR_HEX_LINE_LENGTH = 96;
    public static final int QR_HEX_LINES_PER_QR = 12;
    public static final int QR_HEX_CHUNK_SIZE = QR_HEX_LINE_LENGTH * QR_HEX_LINES_PER_QR;
    public static final int QR_HEX_SPLIT_THRESHOLD = QR_HEX_CHUNK_SIZE;

    private final Wallet wallet;
    private final int timelockDays;
    private final double feeRate;
    private final List<TimelockRecoveryRecipient> recipients;
    private final WalletNode initiationNode;
    private final WalletNode cancellationNode;

    private final WalletTransaction initiationWalletTx;
    private final PSBT initiationPsbt;
    private final int initiationVout;
    private final long initiationOutputValue;
    private final long relativeSequence;

    private WalletTransaction recoveryWalletTx;
    private PSBT recoveryPsbt;

    private WalletTransaction cancellationWalletTx;
    private PSBT cancellationPsbt;

    private Transaction signedInitiationTx;
    private Transaction signedRecoveryTx;
    private Transaction signedCancellationTx;

    private TimelockRecovery(Wallet wallet, int timelockDays, double feeRate, List<TimelockRecoveryRecipient> recipients,
                             WalletNode initiationNode, WalletNode cancellationNode, WalletTransaction initiationWalletTx, PSBT initiationPsbt,
                             int initiationVout, long initiationOutputValue, long relativeSequence) {
        this.wallet = wallet;
        this.timelockDays = timelockDays;
        this.feeRate = feeRate;
        this.recipients = recipients;
        this.initiationNode = initiationNode;
        this.cancellationNode = cancellationNode;
        this.initiationWalletTx = initiationWalletTx;
        this.initiationPsbt = initiationPsbt;
        this.initiationVout = initiationVout;
        this.initiationOutputValue = initiationOutputValue;
        this.relativeSequence = relativeSequence;
    }

    public static boolean isEligible(Wallet wallet) {
        if(wallet == null || !wallet.isValid()) {
            return false;
        }
        ScriptType scriptType = wallet.getScriptType();
        if(scriptType != ScriptType.P2WPKH && scriptType != ScriptType.P2WSH) {
            return false;
        }
        PolicyType policyType = wallet.getPolicyType();
        return policyType == PolicyType.SINGLE_HD || policyType == PolicyType.MULTI_HD;
    }

    public static long relativeTimelockSequence(int days) throws TimelockRecoveryException {
        if(days < MIN_TIMELOCK_DAYS || days > MAX_TIMELOCK_DAYS) {
            throw new TimelockRecoveryException("Cancellation period must be between " + MIN_TIMELOCK_DAYS + " and " + MAX_TIMELOCK_DAYS + " days");
        }
        long units = Math.round(days * 24.0 * 60.0 * 60.0 / TransactionInput.RELATIVE_TIMELOCK_SECONDS_INCREMENT);
        if(units > TransactionInput.RELATIVE_TIMELOCK_VALUE_MASK) {
            throw new TimelockRecoveryException("Sequence number is too large");
        }
        return TransactionInput.RELATIVE_TIMELOCK_TYPE_FLAG | units;
    }

    public static int frozenUtxoCount(Wallet wallet) {
        return (int)wallet.getWalletUtxos().keySet().stream().filter(txo -> txo.getStatus() == Status.FROZEN).count();
    }

    public static TimelockRecovery create(Wallet wallet, List<TimelockRecoveryRecipient> recipients, int timelockDays,
                                          double feeRate, boolean includeCancellation, Integer currentBlockHeight) throws TimelockRecoveryException {
        if(!isEligible(wallet)) {
            throw new TimelockRecoveryException("Timelock Recovery requires a native SegWit (P2WPKH or P2WSH) HD wallet");
        }
        if(recipients == null || recipients.isEmpty()) {
            throw new TimelockRecoveryException("At least one destination address is required");
        }
        if(feeRate <= 0) {
            throw new TimelockRecoveryException("Fee rate must be positive");
        }
        if(wallet.getSpendableUtxos().isEmpty()) {
            throw new TimelockRecoveryException("The wallet has no spendable UTXOs");
        }

        long remainingCount = recipients.stream().filter(TimelockRecoveryRecipient::remaining).count();
        if(remainingCount != 1) {
            throw new TimelockRecoveryException("Exactly one destination must receive the remaining amount");
        }
        for(TimelockRecoveryRecipient recipient : recipients) {
            if(wallet.isWalletAddress(recipient.address())) {
                throw new TimelockRecoveryException("Recovery destinations must not belong to this wallet: " + recipient.address());
            }
            if(!recipient.remaining() && recipient.amountSats() <= ANCHOR_AMOUNT_SATS) {
                throw new TimelockRecoveryException("Each recovery output must be above " + ANCHOR_AMOUNT_SATS + " sats");
            }
        }

        long relativeSequence = relativeTimelockSequence(timelockDays);
        WalletNode initiationNode = reserveInitiationNode(wallet);
        WalletNode cancellationNode = null;
        if(includeCancellation) {
            cancellationNode = reserveCancellationNode(wallet, initiationNode);
        }

        List<Payment> initiationPayments = new ArrayList<>();
        initiationPayments.add(new WalletNodePayment(initiationNode, INITIATION_ADDRESS_LABEL, 0, true));
        for(TimelockRecoveryRecipient recipient : recipients) {
            initiationPayments.add(new Payment(recipient.address(), recipient.label(), ANCHOR_AMOUNT_SATS, false));
        }

        double minRelayFeeRate = Transaction.DEFAULT_MIN_RELAY_FEE;
        double longTermFeeRate = Math.max(Transaction.DUST_RELAY_TX_FEE, minRelayFeeRate);
        TransactionParameters params = new TransactionParameters(
                List.of(new MaxUtxoSelector()),
                List.of(new SpentTxoFilter(), new FrozenTxoFilter(), new CoinbaseTxoFilter(wallet)),
                initiationPayments,
                List.of(),
                Set.of(),
                feeRate,
                longTermFeeRate,
                minRelayFeeRate,
                null,
                currentBlockHeight,
                false,
                true,
                true);

        WalletTransaction initiationWalletTx;
        try {
            initiationWalletTx = wallet.createWalletTransaction(params);
        } catch(InsufficientFundsException e) {
            throw new TimelockRecoveryException("Not enough funds to create the Initiation transaction", e);
        }

        for(TransactionInput input : initiationWalletTx.getTransaction().getInputs()) {
            input.setSequenceNumber(TransactionInput.SEQUENCE_RBF_ENABLED);
        }

        PSBT initiationPsbt = initiationWalletTx.createPSBT();
        WalletTransaction shuffledInitiationWalletTx = remapWalletTransaction(initiationWalletTx, initiationPsbt.getTransaction());
        Transaction unsignedInitiation = initiationPsbt.getTransaction();
        int initiationVout = findInitiationVout(unsignedInitiation, initiationNode.getAddress());
        long initiationOutputValue = unsignedInitiation.getOutputs().get(initiationVout).getValue();

        TimelockRecovery recovery = new TimelockRecovery(wallet, timelockDays, feeRate, List.copyOf(recipients),
                initiationNode, cancellationNode, shuffledInitiationWalletTx, initiationPsbt, initiationVout, initiationOutputValue, relativeSequence);
        recovery.buildChildTransactions(unsignedInitiation);
        return recovery;
    }

    private void buildChildTransactions(Transaction initiationTx) throws TimelockRecoveryException {
        this.recoveryPsbt = createChildPsbt(initiationTx, relativeSequence, recoveryPayments(initiationTx), false);
        this.recoveryWalletTx = toWalletTransaction(recoveryPsbt.getTransaction(), initiationTx, recoveryPayments(initiationTx), false);

        if(cancellationNode != null) {
            WalletNodePayment cancelPayment = new WalletNodePayment(cancellationNode, CANCELLATION_ADDRESS_LABEL, 0, true);
            this.cancellationPsbt = createChildPsbt(initiationTx, TransactionInput.SEQUENCE_RBF_ENABLED, List.of(cancelPayment), true);
            this.cancellationWalletTx = toWalletTransaction(cancellationPsbt.getTransaction(), initiationTx, List.of(cancelPayment), true);
        }
    }

    private List<Payment> recoveryPayments(Transaction initiationTx) throws TimelockRecoveryException {
        long fee = requiredFee(initiationTx, relativeSequence, destinationScripts());
        long allocated = 0;
        int remainingIndex = -1;
        for(int i = 0; i < recipients.size(); i++) {
            if(recipients.get(i).remaining()) {
                remainingIndex = i;
            } else {
                allocated += recipients.get(i).amountSats();
            }
        }
        long leftover = initiationOutputValue - allocated - fee;
        if(leftover <= ANCHOR_AMOUNT_SATS) {
            throw new TimelockRecoveryException("Not enough value in the Initiation output to fund the Recovery transaction after fees");
        }

        List<Payment> payments = new ArrayList<>();
        for(int i = 0; i < recipients.size(); i++) {
            TimelockRecoveryRecipient recipient = recipients.get(i);
            long amount = i == remainingIndex ? leftover : recipient.amountSats();
            payments.add(new Payment(recipient.address(), recipient.label(), amount, i == remainingIndex));
        }
        return payments;
    }

    private List<Script> destinationScripts() {
        return recipients.stream().map(recipient -> recipient.address().getOutputScript()).toList();
    }

    private long requiredFee(Transaction initiationTx, long sequence, List<Script> outputScripts) {
        Transaction dummy = new Transaction();
        dummy.setVersion(2);
        dummy.setLocktime(initiationTx.getLocktime());
        TransactionOutput prevOut = initiationTx.getOutputs().get(initiationVout);
        TransactionInput dummyInput = Wallet.addDummySpendingInput(dummy, initiationNode, prevOut);
        dummyInput.setSequenceNumber(sequence);
        for(Script script : outputScripts) {
            dummy.addOutput(ANCHOR_AMOUNT_SATS, script);
        }
        return (long)Math.floor(feeRate * dummy.getVirtualSize());
    }

    private PSBT createChildPsbt(Transaction initiationTx, long sequence, List<Payment> payments, boolean sendMaxToWallet) throws TimelockRecoveryException {
        long fee = requiredFee(initiationTx, sequence, payments.stream().map(payment -> payment.getAddress().getOutputScript()).toList());
        long allocated = payments.stream().filter(payment -> !payment.isSendMax()).mapToLong(Payment::getAmount).sum();
        long leftover = initiationOutputValue - allocated - fee;
        if(leftover < ANCHOR_AMOUNT_SATS) {
            throw new TimelockRecoveryException("Not enough value in the Initiation output after fees");
        }
        for(Payment payment : payments) {
            if(payment.isSendMax()) {
                payment.setAmount(leftover);
            }
        }

        Transaction child = new Transaction();
        child.setVersion(2);
        child.setLocktime(initiationTx.getLocktime());
        TransactionInput childInput = child.addInput(initiationTx.getTxId(), initiationVout, new Script(new byte[0]));
        childInput.setSequenceNumber(sequence);
        for(Payment payment : payments) {
            child.addOutput(payment.getAmount(), payment.getAddress());
        }

        PSBT psbt = new PSBT(child);
        fillSpendMetadata(psbt.getPsbtInputs().get(0), initiationTx, sequence);
        if(sendMaxToWallet && cancellationNode != null && !psbt.getPsbtOutputs().isEmpty()) {
            fillOutputMetadata(psbt.getPsbtOutputs().get(0), cancellationNode);
        }
        return psbt;
    }

    private void fillSpendMetadata(PSBTInput psbtInput, Transaction prevTx, long sequence) {
        TransactionOutput utxo = prevTx.getOutputs().get(initiationVout);
        psbtInput.setWitnessUtxo(utxo);
        psbtInput.setNonWitnessUtxo(prevTx);
        psbtInput.setSequence(sequence);
        psbtInput.setSigHash(com.sparrowwallet.drongo.protocol.SigHash.ALL);

        Transaction dummy = new Transaction();
        TransactionInput dummyInput = Wallet.addDummySpendingInput(dummy, initiationNode, utxo);
        if(dummyInput.getWitness() != null && dummyInput.getWitness().getWitnessScript() != null) {
            psbtInput.setWitnessScript(dummyInput.getWitness().getWitnessScript());
        }

        for(Keystore keystore : wallet.getKeystores()) {
            ECKey pubKey = wallet.getScriptType().getOutputKey(wallet.getPolicyType(), keystore.getPubKey(initiationNode));
            psbtInput.getDerivedPublicKeys().put(pubKey, keystore.getKeyDerivation().extend(initiationNode.getDerivation()));
        }
    }

    private void fillOutputMetadata(PSBTOutput psbtOutput, WalletNode node) {
        Transaction dummy = new Transaction();
        TransactionOutput dummyOut = new TransactionOutput(dummy, 1L, node.getOutputScript());
        TransactionInput dummyInput = Wallet.addDummySpendingInput(dummy, node, dummyOut);
        if(dummyInput.getWitness() != null && dummyInput.getWitness().getWitnessScript() != null) {
            psbtOutput.setWitnessScript(dummyInput.getWitness().getWitnessScript());
        }
        for(Keystore keystore : wallet.getKeystores()) {
            ECKey pubKey = wallet.getScriptType().getOutputKey(wallet.getPolicyType(), keystore.getPubKey(node));
            psbtOutput.getDerivedPublicKeys().put(pubKey, keystore.getKeyDerivation().extend(node.getDerivation()));
        }
    }

    private WalletTransaction toWalletTransaction(Transaction tx, Transaction initiationTx, List<Payment> payments, boolean consolidation) {
        Map<BlockTransactionHashIndex, WalletNode> selected = new LinkedHashMap<>();
        selected.put(new BlockTransactionHashIndex(initiationTx.getTxId(), 0, new Date(), initiationWalletTx.getFee(), initiationVout, initiationOutputValue), initiationNode);

        List<WalletTransaction.Output> outputs = new ArrayList<>();
        List<Payment> diagramPayments = new ArrayList<>();
        long outputTotal = 0;
        for(int i = 0; i < tx.getOutputs().size(); i++) {
            TransactionOutput txOut = tx.getOutputs().get(i);
            outputTotal += txOut.getValue();
            Payment payment = i < payments.size() ? copyPayment(payments.get(i), txOut.getValue()) : new Payment(txOut.getScript().getToAddress(), null, txOut.getValue(), false);
            diagramPayments.add(payment);
            if(consolidation && payment instanceof WalletNodePayment walletNodePayment) {
                outputs.add(new WalletTransaction.ConsolidationOutput(txOut, walletNodePayment, txOut.getValue()));
            } else {
                outputs.add(new WalletTransaction.PaymentOutput(txOut, payment));
            }
        }

        long fee = initiationOutputValue - outputTotal;
        return new WalletTransaction(wallet, tx, List.of(), List.of(selected), diagramPayments, outputs, fee);
    }

    private static Payment copyPayment(Payment payment, long amount) {
        if(payment instanceof WalletNodePayment walletNodePayment) {
            return new WalletNodePayment(walletNodePayment.getWalletNode(), payment.getLabel(), amount, payment.isSendMax());
        }
        return new Payment(payment.getAddress(), payment.getLabel(), amount, payment.isSendMax());
    }

    static WalletTransaction remapWalletTransaction(WalletTransaction original, Transaction shuffled) throws TimelockRecoveryException {
        List<WalletTransaction.Output> remaining = new ArrayList<>(original.getOutputs());
        List<WalletTransaction.Output> remapped = new ArrayList<>();
        List<Payment> remappedPayments = new ArrayList<>();
        for(TransactionOutput txOut : shuffled.getOutputs()) {
            WalletTransaction.Output match = null;
            for(Iterator<WalletTransaction.Output> iterator = remaining.iterator(); iterator.hasNext(); ) {
                WalletTransaction.Output candidate = iterator.next();
                TransactionOutput originalOut = candidate.getTransactionOutput();
                if(originalOut.getValue() == txOut.getValue() && Arrays.equals(originalOut.getScript().getProgram(), txOut.getScript().getProgram())) {
                    match = candidate;
                    iterator.remove();
                    break;
                }
            }
            if(match == null) {
                throw new TimelockRecoveryException("Could not map shuffled Initiation outputs");
            }
            if(match instanceof WalletTransaction.ConsolidationOutput consolidationOutput) {
                WalletNodePayment payment = (WalletNodePayment)copyPayment(consolidationOutput.getWalletNodePayment(), txOut.getValue());
                remappedPayments.add(payment);
                remapped.add(new WalletTransaction.ConsolidationOutput(txOut, payment, txOut.getValue()));
            } else if(match instanceof WalletTransaction.PaymentOutput paymentOutput) {
                Payment payment = copyPayment(paymentOutput.getPayment(), txOut.getValue());
                remappedPayments.add(payment);
                remapped.add(new WalletTransaction.PaymentOutput(txOut, payment));
            } else if(match instanceof WalletTransaction.ChangeOutput changeOutput) {
                remapped.add(new WalletTransaction.ChangeOutput(txOut, changeOutput.getWalletNode(), txOut.getValue()));
            } else {
                Payment payment = new Payment(txOut.getScript().getToAddress(), null, txOut.getValue(), false);
                remappedPayments.add(payment);
                remapped.add(new WalletTransaction.PaymentOutput(txOut, payment));
            }
        }
        return new WalletTransaction(original.getWallet(), shuffled, original.getUtxoSelectors(), original.getSelectedUtxoSets(),
                remappedPayments, remapped, original.getChangeMap(), original.getFee(), original.getInputTransactions());
    }

    public static List<String> splitHexForQr(String hex) {
        if(hex == null || hex.isEmpty()) {
            return List.of();
        }
        if(hex.length() <= QR_HEX_CHUNK_SIZE) {
            return List.of(hex);
        }
        List<String> parts = new ArrayList<>();
        for(int i = 0; i < hex.length(); i += QR_HEX_CHUNK_SIZE) {
            parts.add(hex.substring(i, Math.min(i + QR_HEX_CHUNK_SIZE, hex.length())));
        }
        return parts;
    }

    public static String networkExplorerPath() {
        return Network.get() == Network.MAINNET ? "" : "/" + Network.get().getName();
    }

    public static String mempoolPushUrl() {
        return "https://mempool.space" + networkExplorerPath() + "/tx/push";
    }

    public static String blockstreamPushUrl() {
        return "https://blockstream.info" + networkExplorerPath() + "/tx/push";
    }

    public static String coinbinBroadcastUrl() {
        return "https://coinb.in/#broadcast";
    }

    public static String mempoolTxUrl(String txid) {
        return "https://mempool.space" + networkExplorerPath() + "/tx/" + txid;
    }

    public static String blockstreamTxUrl(String txid) {
        return "https://blockstream.info" + networkExplorerPath() + "/tx/" + txid;
    }

    static int findInitiationVout(Transaction initiationTx, Address initiationAddress) throws TimelockRecoveryException {
        int found = -1;
        for(int i = 0; i < initiationTx.getOutputs().size(); i++) {
            TransactionOutput output = initiationTx.getOutputs().get(i);
            Address outputAddress = output.getScript().getToAddress();
            if(initiationAddress.equals(outputAddress) && output.getValue() != ANCHOR_AMOUNT_SATS) {
                if(found >= 0) {
                    throw new TimelockRecoveryException("Expected 1 output from the Initiation transaction to the Initiation address, but found more than one");
                }
                found = i;
            }
        }
        if(found < 0) {
            throw new TimelockRecoveryException("Expected 1 output from the Initiation transaction to the Initiation address");
        }
        return found;
    }

    public static WalletNode reserveInitiationNode(Wallet wallet) {
        WalletNode initiationNode = labeledOrFreshReceiveNode(wallet, INITIATION_ADDRESS_LABEL, null);
        initiationNode.setLabel(INITIATION_ADDRESS_LABEL);
        return initiationNode;
    }

    public static WalletNode reserveCancellationNode(Wallet wallet, WalletNode after) {
        WalletNode cancellationNode = labeledOrFreshReceiveNode(wallet, CANCELLATION_ADDRESS_LABEL, after);
        cancellationNode.setLabel(CANCELLATION_ADDRESS_LABEL);
        return cancellationNode;
    }

    static WalletNode labeledOrFreshReceiveNode(Wallet wallet, String label, WalletNode after) {
        WalletNode purposeNode = wallet.getNode(KeyPurpose.RECEIVE);
        for(WalletNode child : purposeNode.getChildren()) {
            if(label.equals(child.getLabel()) && child.getTransactionOutputs().isEmpty() && (after == null || child.getIndex() > after.getIndex())) {
                return child;
            }
        }

        WalletNode previous = after;
        while(true) {
            WalletNode candidate = wallet.getFreshNode(KeyPurpose.RECEIVE, previous);
            if(candidate.getLabel() == null || candidate.getLabel().isBlank()) {
                return candidate;
            }
            previous = candidate;
        }
    }

    public void applySignedInitiation(Transaction signedInitiation) throws TimelockRecoveryException {
        if(!signedInitiation.getTxId().equals(getInitiationTxid())) {
            throw new TimelockRecoveryException("Signed Initiation transaction does not match the prepared Initiation transaction");
        }
        this.signedInitiationTx = signedInitiation;
        buildChildTransactions(signedInitiation);
    }

    public Transaction extractSigned(PSBT psbt) throws TimelockRecoveryException {
        try {
            if(psbt.isSigned() && !psbt.isFinalized()) {
                wallet.finalise(psbt);
            }
            if(!psbt.isFinalized()) {
                throw new TimelockRecoveryException("Transaction is not fully signed");
            }
            return psbt.extractTransaction();
        } catch(PSBTProofException e) {
            throw new TimelockRecoveryException("Cannot extract signed transaction", e);
        }
    }

    public Transaction extractSignedInitiation() throws TimelockRecoveryException {
        signedInitiationTx = extractSigned(initiationPsbt);
        applySignedInitiation(signedInitiationTx);
        return signedInitiationTx;
    }

    public Transaction extractSignedRecovery() throws TimelockRecoveryException {
        signedRecoveryTx = extractSigned(recoveryPsbt);
        return signedRecoveryTx;
    }

    public Transaction extractSignedCancellation() throws TimelockRecoveryException {
        if(cancellationPsbt == null) {
            throw new TimelockRecoveryException("No cancellation transaction was created");
        }
        signedCancellationTx = extractSigned(cancellationPsbt);
        return signedCancellationTx;
    }

    public static String toUpperHex(Transaction transaction) {
        return Utils.bytesToHex(transaction.bitcoinSerialize()).toUpperCase(Locale.ROOT);
    }

    public Wallet getWallet() {
        return wallet;
    }

    public int getTimelockDays() {
        return timelockDays;
    }

    public double getFeeRate() {
        return feeRate;
    }

    public List<TimelockRecoveryRecipient> getRecipients() {
        return recipients;
    }

    public WalletNode getInitiationNode() {
        return initiationNode;
    }

    public WalletNode getCancellationNode() {
        return cancellationNode;
    }

    public Address getInitiationAddress() {
        return initiationNode.getAddress();
    }

    public Address getCancellationAddress() {
        return cancellationNode == null ? null : cancellationNode.getAddress();
    }

    public WalletTransaction getInitiationWalletTx() {
        return initiationWalletTx;
    }

    public WalletTransaction getRecoveryWalletTx() {
        return recoveryWalletTx;
    }

    public WalletTransaction getCancellationWalletTx() {
        return cancellationWalletTx;
    }

    public PSBT getInitiationPsbt() {
        return initiationPsbt;
    }

    public PSBT getRecoveryPsbt() {
        return recoveryPsbt;
    }

    public PSBT getCancellationPsbt() {
        return cancellationPsbt;
    }

    public long getRelativeSequence() {
        return relativeSequence;
    }

    public int getInitiationVout() {
        return initiationVout;
    }

    public Sha256Hash getInitiationTxid() {
        return initiationPsbt.getTransaction().getTxId();
    }

    public Transaction getSignedInitiationTx() {
        return signedInitiationTx;
    }

    public Transaction getSignedRecoveryTx() {
        return signedRecoveryTx;
    }

    public Transaction getSignedCancellationTx() {
        return signedCancellationTx;
    }

    public boolean hasCancellation() {
        return cancellationPsbt != null;
    }

    public List<Address> getAnchorAddresses() {
        return recipients.stream().map(TimelockRecoveryRecipient::address).toList();
    }

    public List<String> getInitiationInputRefs() {
        return initiationPsbt.getTransaction().getInputs().stream()
                .map(input -> input.getOutpoint().getHash().toString() + ":" + input.getOutpoint().getIndex())
                .toList();
    }
}
