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
import com.sparrowwallet.drongo.uri.BitcoinURI;
import com.sparrowwallet.sparrow.AppServices;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;

public class PayjoinURITest {
    private static final ECKey SENDER_KEY = ECKey.fromPrivate(BigInteger.valueOf(1001));
    private static final ECKey CHANGE_KEY = ECKey.fromPrivate(BigInteger.valueOf(1002));
    private static final ECKey PAYMENT_KEY = ECKey.fromPrivate(BigInteger.valueOf(1003));

    private static final Sha256Hash SENDER_UTXO_HASH = Sha256Hash.wrap("1111111111111111111111111111111111111111111111111111111111111111");
    private static final Sha256Hash OTHER_UTXO_HASH = Sha256Hash.wrap("3333333333333333333333333333333333333333333333333333333333333333");

    private static final long SENDER_UTXO_VALUE = 200000L;
    private static final long PAYMENT_VALUE = 100000L;
    private static final long CHANGE_VALUE = 90000L;

    @AfterEach
    public void clearPayjoinURIs() {
        AppServices.clearPayjoinURI(getOriginalPSBT(SENDER_UTXO_HASH));
        AppServices.clearPayjoinURI(getOriginalPSBT(OTHER_UTXO_HASH));
    }

    @Test
    public void signedTransactionReturnedForBroadcastRetrievesPayjoinURI() throws Exception {
        PSBT original = getOriginalPSBT(SENDER_UTXO_HASH);
        AppServices.addPayjoinURI(original, getPayjoinURI());

        //The signing device is sent the exported PSBT, and returns it signed
        PSBT exported = PSBT.fromString(original.getForExport().toBase64String());
        finalise(exported.getPsbtInputs().get(0), exported.getTransaction());
        PSBT signed = PSBT.fromString(exported.toBase64String());

        Assertions.assertNotNull(AppServices.getPayjoinURI(signed));
    }

    @Test
    public void laterTransactionToTheSameAddressDoesNotRetrievePayjoinURI() throws Exception {
        PSBT original = getOriginalPSBT(SENDER_UTXO_HASH);
        AppServices.addPayjoinURI(original, getPayjoinURI());

        //A later payment to the same address spending a different utxo
        PSBT later = getOriginalPSBT(OTHER_UTXO_HASH);

        Assertions.assertNull(AppServices.getPayjoinURI(later));
    }

    @Test
    public void clearedPayjoinURIIsNotRetrieved() throws Exception {
        PSBT original = getOriginalPSBT(SENDER_UTXO_HASH);
        AppServices.addPayjoinURI(original, getPayjoinURI());
        Assertions.assertNotNull(AppServices.getPayjoinURI(original));

        AppServices.clearPayjoinURI(original);
        Assertions.assertNull(AppServices.getPayjoinURI(original));
    }

    private BitcoinURI getPayjoinURI() throws Exception {
        return new BitcoinURI("bitcoin:" + ScriptType.P2WPKH.getAddress(PAYMENT_KEY.getPubKeyHash()) + "?pj=https://payjoin.example.com/pj");
    }

    private PSBT getOriginalPSBT(Sha256Hash utxoHash) {
        Transaction transaction = new Transaction();
        transaction.setVersion(2);
        transaction.addInput(utxoHash, 0, new Script(new byte[0]));
        transaction.addOutput(PAYMENT_VALUE, ScriptType.P2WPKH.getOutputScript(PAYMENT_KEY.getPubKeyHash()));
        transaction.addOutput(CHANGE_VALUE, ScriptType.P2WPKH.getOutputScript(CHANGE_KEY.getPubKeyHash()));

        //Sparrow creates PSBTv2, which is exported as PSBTv0 where no silent payments are present
        PSBT psbt = new PSBT(transaction);
        psbt.getPsbtInputs().get(0).setWitnessUtxo(new TransactionOutput(null, SENDER_UTXO_VALUE, ScriptType.P2WPKH.getOutputScript(SENDER_KEY.getPubKeyHash())));

        return psbt;
    }

    private void finalise(PSBTInput psbtInput, Transaction transaction) {
        psbtInput.setFinalScriptSig(new Script(new byte[0]));
        psbtInput.setFinalScriptWitness(new TransactionWitness(transaction, List.of(new byte[71], SENDER_KEY.getPubKey())));
    }
}
