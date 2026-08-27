package com.sparrowwallet.sparrow.payjoin;

import com.sparrowwallet.drongo.crypto.ECKey;
import com.sparrowwallet.drongo.protocol.Script;
import com.sparrowwallet.drongo.protocol.ScriptType;
import com.sparrowwallet.drongo.protocol.Sha256Hash;
import com.sparrowwallet.drongo.protocol.Transaction;
import com.sparrowwallet.drongo.protocol.TransactionOutput;
import com.sparrowwallet.drongo.protocol.TransactionWitness;
import com.sparrowwallet.drongo.psbt.PSBT;
import com.sparrowwallet.drongo.psbt.PSBTInput;
import com.sparrowwallet.drongo.silentpayments.SilentPaymentAddress;
import com.sparrowwallet.drongo.uri.BitcoinURI;
import com.sparrowwallet.drongo.wallet.Wallet;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;

public class PayjoinTest {
    private static final ECKey SENDER_KEY = ECKey.fromPrivate(BigInteger.valueOf(1001));
    private static final ECKey CHANGE_KEY = ECKey.fromPrivate(BigInteger.valueOf(1002));
    private static final ECKey PAYMENT_KEY = ECKey.fromPrivate(BigInteger.valueOf(1003));
    private static final ECKey SUBSTITUTE_KEY = ECKey.fromPrivate(BigInteger.valueOf(1004));
    private static final ECKey RECEIVER_KEY = ECKey.fromPrivate(BigInteger.valueOf(1005));
    private static final ECKey SCAN_KEY = ECKey.fromPrivate(BigInteger.valueOf(1006));
    private static final ECKey SPEND_KEY = ECKey.fromPrivate(BigInteger.valueOf(1007));

    private static final Sha256Hash SENDER_UTXO_HASH = Sha256Hash.wrap("1111111111111111111111111111111111111111111111111111111111111111");
    private static final Sha256Hash RECEIVER_UTXO_HASH = Sha256Hash.wrap("2222222222222222222222222222222222222222222222222222222222222222");

    private static final long SENDER_UTXO_VALUE = 200000L;
    private static final long RECEIVER_UTXO_VALUE = 150000L;
    private static final long PAYMENT_VALUE = 100000L;
    private static final long CHANGE_VALUE = 90000L;
    private static final int CHANGE_OUTPUT_INDEX = 1;
    private static final long MAX_ADDITIONAL_FEE_CONTRIBUTION = 5000L;
    private static final double MIN_FEE_RATE = 1.0d;

    @Test
    public void unsubstitutedProposalIsAccepted() throws Exception {
        PSBT original = getOriginalPSBT();
        Payjoin payjoin = getPayjoin(original);
        PSBT proposal = getProposalPSBT(getPaymentScript(), PAYMENT_VALUE + RECEIVER_UTXO_VALUE, CHANGE_VALUE);

        payjoin.checkProposal(original, proposal, CHANGE_OUTPUT_INDEX, MAX_ADDITIONAL_FEE_CONTRIBUTION, MIN_FEE_RATE, true);
    }

    @Test
    public void substitutedPaymentOutputIsAcceptedWhenChangeOutputIsPresent() throws Exception {
        PSBT original = getOriginalPSBT();
        Payjoin payjoin = getPayjoin(original);
        PSBT proposal = getProposalPSBT(getSubstituteScript(), PAYMENT_VALUE + RECEIVER_UTXO_VALUE, CHANGE_VALUE);

        payjoin.checkProposal(original, proposal, CHANGE_OUTPUT_INDEX, MAX_ADDITIONAL_FEE_CONTRIBUTION, MIN_FEE_RATE, true);
    }

    @Test
    public void substitutedPaymentOutputIsRejectedWhenSubstitutionIsDisallowed() throws Exception {
        PSBT original = getOriginalPSBT();
        Payjoin payjoin = getPayjoin(original);
        PSBT proposal = getProposalPSBT(getSubstituteScript(), PAYMENT_VALUE + RECEIVER_UTXO_VALUE, CHANGE_VALUE);

        PayjoinReceiverException e = Assertions.assertThrows(PayjoinReceiverException.class,
                () -> payjoin.checkProposal(original, proposal, CHANGE_OUTPUT_INDEX, MAX_ADDITIONAL_FEE_CONTRIBUTION, MIN_FEE_RATE, false));
        Assertions.assertEquals("Some of our outputs are not included in the proposal", e.getMessage());
    }

    @Test
    public void changeOutputIsStillCheckedWhenPaymentOutputIsSubstituted() throws Exception {
        PSBT original = getOriginalPSBT();
        Payjoin payjoin = getPayjoin(original);
        PSBT proposal = getProposalPSBT(getSubstituteScript(), PAYMENT_VALUE + RECEIVER_UTXO_VALUE, CHANGE_VALUE - MAX_ADDITIONAL_FEE_CONTRIBUTION - 1);

        PayjoinReceiverException e = Assertions.assertThrows(PayjoinReceiverException.class,
                () -> payjoin.checkProposal(original, proposal, CHANGE_OUTPUT_INDEX, MAX_ADDITIONAL_FEE_CONTRIBUTION, MIN_FEE_RATE, true));
        Assertions.assertEquals("The actual contribution is more than maxadditionalfeecontribution", e.getMessage());
    }

    @Test
    public void proposalBelowRequestedMinFeeRateIsRejected() throws Exception {
        PSBT original = getOriginalPSBT();
        Payjoin payjoin = getPayjoin(original);
        //The receiver adds an input without increasing the fee, dropping the fee rate of the payjoin transaction
        PSBT proposal = getProposalPSBT(getPaymentScript(), PAYMENT_VALUE + RECEIVER_UTXO_VALUE, CHANGE_VALUE);

        PayjoinReceiverException e = Assertions.assertThrows(PayjoinReceiverException.class,
                () -> payjoin.checkProposal(original, proposal, CHANGE_OUTPUT_INDEX, MAX_ADDITIONAL_FEE_CONTRIBUTION, 100.0d, true));
        Assertions.assertTrue(e.getMessage().contains("is less than the requested minimum"));
    }

    @Test
    public void proposalWithOversizedReceiverWitnessIsRejected() throws Exception {
        PSBT original = getOriginalPSBT();
        Payjoin payjoin = getPayjoin(original);
        //The receiver finalizes its input with an oversized witness, dropping the fee rate of the payjoin transaction to around 3.7 sats/vB
        PSBT proposal = getProposalPSBT(getPaymentScript(), PAYMENT_VALUE + RECEIVER_UTXO_VALUE, CHANGE_VALUE, 10000);

        PayjoinReceiverException e = Assertions.assertThrows(PayjoinReceiverException.class,
                () -> payjoin.checkProposal(original, proposal, CHANGE_OUTPUT_INDEX, MAX_ADDITIONAL_FEE_CONTRIBUTION, 10.0d, true));
        Assertions.assertTrue(e.getMessage().contains("is less than the requested minimum"));

        //The same proposal is still accepted where it pays the requested minimum
        payjoin.checkProposal(original, getProposalPSBT(getPaymentScript(), PAYMENT_VALUE + RECEIVER_UTXO_VALUE, CHANGE_VALUE, 10000), CHANGE_OUTPUT_INDEX, MAX_ADDITIONAL_FEE_CONTRIBUTION, MIN_FEE_RATE, true);
    }

    @Test
    public void originalWithSilentPaymentOutputIsRejected() throws Exception {
        PSBT original = getOriginalPSBT();
        //The silent payment output script is derived from the original inputs, so the receiver adding an input would leave it undetectable to its recipient
        original.getPsbtOutputs().get(0).setSilentPaymentAddress(new SilentPaymentAddress(SCAN_KEY, SPEND_KEY));

        IllegalArgumentException e = Assertions.assertThrows(IllegalArgumentException.class, () -> getPayjoin(original));
        Assertions.assertEquals("Original PSBT for payjoin transaction cannot contain silent payment outputs", e.getMessage());
    }

    private Payjoin getPayjoin(PSBT original) throws Exception {
        Wallet wallet = new Wallet();
        wallet.setScriptType(ScriptType.P2WPKH);
        BitcoinURI payjoinURI = new BitcoinURI("bitcoin:" + ScriptType.P2WPKH.getAddress(PAYMENT_KEY.getPubKeyHash()) + "?pj=https://payjoin.example.com/pj");

        return new Payjoin(payjoinURI, wallet, original);
    }

    private PSBT getOriginalPSBT() {
        Transaction transaction = new Transaction();
        transaction.setVersion(2);
        transaction.addInput(SENDER_UTXO_HASH, 0, new Script(new byte[0]));
        transaction.addOutput(PAYMENT_VALUE, getPaymentScript());
        transaction.addOutput(CHANGE_VALUE, getChangeScript());

        PSBT psbt = new PSBT(transaction);
        psbt.convertVersion(0);
        finalise(psbt.getPsbtInputs().get(0), psbt.getTransaction(), SENDER_UTXO_VALUE, getSenderScript(), SENDER_KEY);

        return psbt;
    }

    private PSBT getProposalPSBT(Script paymentScript, long paymentValue, long changeValue) {
        return getProposalPSBT(paymentScript, paymentValue, changeValue, 71);
    }

    private PSBT getProposalPSBT(Script paymentScript, long paymentValue, long changeValue, int receiverSignatureLength) {
        Transaction transaction = new Transaction();
        transaction.setVersion(2);
        transaction.addInput(SENDER_UTXO_HASH, 0, new Script(new byte[0]));
        transaction.addInput(RECEIVER_UTXO_HASH, 0, new Script(new byte[0]));
        transaction.addOutput(paymentValue, paymentScript);
        transaction.addOutput(changeValue, getChangeScript());

        PSBT psbt = new PSBT(transaction);
        psbt.convertVersion(0);
        finalise(psbt.getPsbtInputs().get(1), psbt.getTransaction(), RECEIVER_UTXO_VALUE, getReceiverScript(), RECEIVER_KEY, receiverSignatureLength);

        return psbt;
    }

    private void finalise(PSBTInput psbtInput, Transaction transaction, long value, Script script, ECKey key) {
        finalise(psbtInput, transaction, value, script, key, 71);
    }

    private void finalise(PSBTInput psbtInput, Transaction transaction, long value, Script script, ECKey key, int signatureLength) {
        psbtInput.setWitnessUtxo(new TransactionOutput(null, value, script));
        psbtInput.setFinalScriptSig(new Script(new byte[0]));
        psbtInput.setFinalScriptWitness(new TransactionWitness(transaction, List.of(new byte[signatureLength], key.getPubKey())));
    }

    private Script getSenderScript() {
        return ScriptType.P2WPKH.getOutputScript(SENDER_KEY.getPubKeyHash());
    }

    private Script getChangeScript() {
        return ScriptType.P2WPKH.getOutputScript(CHANGE_KEY.getPubKeyHash());
    }

    private Script getPaymentScript() {
        return ScriptType.P2WPKH.getOutputScript(PAYMENT_KEY.getPubKeyHash());
    }

    private Script getSubstituteScript() {
        return ScriptType.P2WPKH.getOutputScript(SUBSTITUTE_KEY.getPubKeyHash());
    }

    private Script getReceiverScript() {
        return ScriptType.P2WPKH.getOutputScript(RECEIVER_KEY.getPubKeyHash());
    }
}
