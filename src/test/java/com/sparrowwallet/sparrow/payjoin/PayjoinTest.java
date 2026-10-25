package com.sparrowwallet.sparrow.payjoin;

import com.sparrowwallet.drongo.crypto.ECKey;
import com.sparrowwallet.drongo.policy.PolicyType;
import com.sparrowwallet.drongo.protocol.Script;
import com.sparrowwallet.drongo.protocol.ScriptType;
import com.sparrowwallet.drongo.protocol.Sha256Hash;
import com.sparrowwallet.drongo.protocol.Transaction;
import com.sparrowwallet.drongo.protocol.TransactionOutput;
import com.sparrowwallet.drongo.protocol.TransactionSignature;
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

    @Test
    public void receiverInputWithValidSignatureIsAccepted() throws Exception {
        PSBT original = getOriginalPSBT();
        Payjoin payjoin = getPayjoin(original);
        PSBT proposal = getProposalPSBT(getPaymentScript(), PAYMENT_VALUE + RECEIVER_UTXO_VALUE, CHANGE_VALUE);

        payjoin.checkProposal(original, proposal, CHANGE_OUTPUT_INDEX, MAX_ADDITIONAL_FEE_CONTRIBUTION, MIN_FEE_RATE, true);
        Assertions.assertEquals(SENDER_UTXO_VALUE + RECEIVER_UTXO_VALUE - PAYMENT_VALUE - RECEIVER_UTXO_VALUE - CHANGE_VALUE, proposal.getFee());
    }

    @Test
    public void receiverInputSignedForDifferentAmountIsRejected() throws Exception {
        PSBT original = getOriginalPSBT();
        Payjoin payjoin = getPayjoin(original);
        long difference = 5000L;
        PSBT proposal = getProposalPSBT(ScriptType.P2WPKH, getPaymentScript(), PAYMENT_VALUE + RECEIVER_UTXO_VALUE + difference, CHANGE_VALUE, 0, RECEIVER_UTXO_VALUE + difference);

        PayjoinReceiverException e = Assertions.assertThrows(PayjoinReceiverException.class,
                () -> payjoin.checkProposal(original, proposal, CHANGE_OUTPUT_INDEX, MAX_ADDITIONAL_FEE_CONTRIBUTION, MIN_FEE_RATE, true));
        Assertions.assertEquals("The receiver did not provide valid signatures for one of their inputs", e.getMessage());
    }

    @Test
    public void nonSegwitReceiverInputWithoutNonWitnessUtxoIsRejected() throws Exception {
        PSBT original = getOriginalPSBT(ScriptType.P2PKH);
        Payjoin payjoin = getPayjoin(original, ScriptType.P2PKH);
        PSBT proposal = getProposalPSBT(ScriptType.P2PKH, getPaymentScript(), PAYMENT_VALUE + RECEIVER_UTXO_VALUE, CHANGE_VALUE, 0, RECEIVER_UTXO_VALUE);

        PayjoinReceiverException e = Assertions.assertThrows(PayjoinReceiverException.class,
                () -> payjoin.checkProposal(original, proposal, CHANGE_OUTPUT_INDEX, MAX_ADDITIONAL_FEE_CONTRIBUTION, MIN_FEE_RATE, true));
        Assertions.assertEquals("The receiver did not specify non_witness_utxo for one of their non segwit inputs", e.getMessage());
    }

    @Test
    public void taprootReceiverInputIsVerifiedWithOriginalUtxosFilledUp() throws Exception {
        PSBT original = getOriginalPSBT(ScriptType.P2TR);
        Payjoin payjoin = getPayjoin(original, ScriptType.P2TR);

        //The receiver input comes first and the proposal omits the utxo of the original input, which the taproot sighash of the receiver input commits to
        Transaction transaction = new Transaction();
        transaction.setVersion(2);
        transaction.addInput(RECEIVER_UTXO_HASH, 0, new Script(new byte[0]));
        transaction.addInput(SENDER_UTXO_HASH, 0, new Script(new byte[0]));
        transaction.addOutput(PAYMENT_VALUE + RECEIVER_UTXO_VALUE, getPaymentScript());
        transaction.addOutput(CHANGE_VALUE, getChangeScript());

        PSBT proposal = new PSBT(transaction);
        proposal.convertVersion(0);
        PSBTInput senderInput = proposal.getPsbtInputs().get(1);
        senderInput.setWitnessUtxo(original.getPsbtInputs().get(0).getWitnessUtxo());
        finalise(proposal.getPsbtInputs().get(0), proposal.getTransaction(), ScriptType.P2TR, RECEIVER_KEY, RECEIVER_UTXO_VALUE, RECEIVER_UTXO_VALUE, 0);
        senderInput.setWitnessUtxo(null);

        payjoin.checkProposal(original, proposal, CHANGE_OUTPUT_INDEX, MAX_ADDITIONAL_FEE_CONTRIBUTION, MIN_FEE_RATE, true);
    }

    private Payjoin getPayjoin(PSBT original) throws Exception {
        return getPayjoin(original, ScriptType.P2WPKH);
    }

    private Payjoin getPayjoin(PSBT original, ScriptType scriptType) throws Exception {
        Wallet wallet = new Wallet();
        wallet.setScriptType(scriptType);
        BitcoinURI payjoinURI = new BitcoinURI("bitcoin:" + ScriptType.P2WPKH.getAddress(PAYMENT_KEY.getPubKeyHash()) + "?pj=https://payjoin.example.com/pj");

        return new Payjoin(payjoinURI, wallet, original);
    }

    private PSBT getOriginalPSBT() {
        return getOriginalPSBT(ScriptType.P2WPKH);
    }

    private PSBT getOriginalPSBT(ScriptType scriptType) {
        Transaction transaction = new Transaction();
        transaction.setVersion(2);
        transaction.addInput(SENDER_UTXO_HASH, 0, new Script(new byte[0]));
        transaction.addOutput(PAYMENT_VALUE, getPaymentScript());
        transaction.addOutput(CHANGE_VALUE, getChangeScript());

        PSBT psbt = new PSBT(transaction);
        psbt.convertVersion(0);
        finalise(psbt.getPsbtInputs().get(0), psbt.getTransaction(), scriptType, SENDER_KEY, SENDER_UTXO_VALUE, SENDER_UTXO_VALUE, 0);

        return psbt;
    }

    private PSBT getProposalPSBT(Script paymentScript, long paymentValue, long changeValue) {
        return getProposalPSBT(paymentScript, paymentValue, changeValue, 0);
    }

    private PSBT getProposalPSBT(Script paymentScript, long paymentValue, long changeValue, int receiverWitnessPadding) {
        return getProposalPSBT(ScriptType.P2WPKH, paymentScript, paymentValue, changeValue, receiverWitnessPadding, RECEIVER_UTXO_VALUE);
    }

    private PSBT getProposalPSBT(ScriptType scriptType, Script paymentScript, long paymentValue, long changeValue, int receiverWitnessPadding, long receiverUtxoValue) {
        Transaction transaction = new Transaction();
        transaction.setVersion(2);
        transaction.addInput(SENDER_UTXO_HASH, 0, new Script(new byte[0]));
        transaction.addInput(RECEIVER_UTXO_HASH, 0, new Script(new byte[0]));
        transaction.addOutput(paymentValue, paymentScript);
        transaction.addOutput(changeValue, getChangeScript());

        PSBT psbt = new PSBT(transaction);
        psbt.convertVersion(0);
        finalise(psbt.getPsbtInputs().get(1), psbt.getTransaction(), scriptType, RECEIVER_KEY, RECEIVER_UTXO_VALUE, receiverUtxoValue, receiverWitnessPadding);

        return psbt;
    }

    private void finalise(PSBTInput psbtInput, Transaction transaction, ScriptType scriptType, ECKey key, long signedValue, long utxoValue, int witnessPadding) {
        ECKey signingKey = scriptType == ScriptType.P2TR ? key.getTweakedOutputKey() : key;
        Script script = scriptType == ScriptType.P2TR ? ScriptType.P2TR.getOutputScript(signingKey.getPubKeyXCoord()) : scriptType.getOutputScript(key.getPubKeyHash());
        psbtInput.setWitnessUtxo(new TransactionOutput(null, signedValue, script));
        psbtInput.sign(signingKey);
        TransactionSignature signature = scriptType == ScriptType.P2TR ? psbtInput.getTapKeyPathSignature() : psbtInput.getPartialSignatures().values().iterator().next();
        psbtInput.setTapKeyPathSignature(null);
        psbtInput.getPartialSignatures().clear();

        if(scriptType == ScriptType.P2PKH) {
            psbtInput.setFinalScriptSig(ScriptType.P2PKH.getScriptSig(PolicyType.SINGLE_HD, script, key, signature));
        } else if(scriptType == ScriptType.P2TR) {
            psbtInput.setFinalScriptWitness(new TransactionWitness(transaction, List.of(signature.encodeToBitcoin())));
        } else {
            List<byte[]> pushes = witnessPadding > 0 ? List.of(signature.encodeToBitcoin(), key.getPubKey(), new byte[witnessPadding]) : List.of(signature.encodeToBitcoin(), key.getPubKey());
            psbtInput.setFinalScriptSig(new Script(new byte[0]));
            psbtInput.setFinalScriptWitness(new TransactionWitness(transaction, pushes));
        }

        psbtInput.setWitnessUtxo(new TransactionOutput(null, utxoValue, script));
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
}
