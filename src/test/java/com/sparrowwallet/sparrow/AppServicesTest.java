package com.sparrowwallet.sparrow;

import com.sparrowwallet.drongo.protocol.Script;
import com.sparrowwallet.drongo.protocol.Sha256Hash;
import com.sparrowwallet.drongo.protocol.Transaction;
import com.sparrowwallet.drongo.protocol.TransactionInput;
import com.sparrowwallet.drongo.psbt.PSBT;
import com.sparrowwallet.drongo.Utils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class AppServicesTest {
    private Transaction buildTransaction(byte[] scriptSig, long outputValue) {
        Transaction tx = new Transaction();
        tx.setVersion(2);
        tx.setLocktime(850000);
        TransactionInput input = tx.addInput(Sha256Hash.wrap(Utils.hexToBytes("aa00000000000000000000000000000000000000000000000000000000000011")), 1, new Script(scriptSig));
        input.setSequenceNumber(0xfffffffdL);
        tx.addOutput(outputValue, new Script(Utils.hexToBytes("76a914000000000000000000000000000000000000000088ac")));
        return tx;
    }

    @Test
    public void signedLegacyTransactionMatchesSourcePsbt() {
        PSBT psbt = new PSBT(buildTransaction(new byte[0], 12345L));
        Transaction signedTx = buildTransaction(Utils.hexToBytes("483045022100aa11"), 12345L);

        Assertions.assertNotEquals(psbt.getTransaction().getTxId(), signedTx.getTxId(), "Legacy signed txid should differ from unsigned txid");
        Assertions.assertTrue(AppServices.psbtMatchesTransaction(psbt, signedTx));
    }

    @Test
    public void tamperedTransactionDoesNotMatchSourcePsbt() {
        PSBT psbt = new PSBT(buildTransaction(new byte[0], 12345L));
        Transaction tamperedTx = buildTransaction(Utils.hexToBytes("483045022100aa11"), 99999L);

        Assertions.assertFalse(AppServices.psbtMatchesTransaction(psbt, tamperedTx));
    }

    @Test
    public void unsignedTransactionMatchesSourcePsbt() {
        PSBT psbt = new PSBT(buildTransaction(new byte[0], 12345L));
        Transaction sameTx = buildTransaction(new byte[0], 12345L);

        Assertions.assertTrue(AppServices.psbtMatchesTransaction(psbt, sameTx));
    }
}
