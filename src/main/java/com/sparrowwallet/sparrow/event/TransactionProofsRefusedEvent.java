package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.drongo.wallet.BlockTransactionHash;
import com.sparrowwallet.drongo.wallet.Wallet;

import java.util.Set;

/**
 * Posted once per wallet history pass where the server reported a transaction as confirmed and then would not substantiate it at that height, by
 * erroring, by answering for another block, or by leaving the header unverifiable. Nothing has been shown false, so this is the server contradicting
 * itself rather than lying. The transactions are already written unconfirmed.
 * <p>
 * Dispatched on the wallet history thread, so a handler must hop to the application thread itself.
 */
public class TransactionProofsRefusedEvent extends TransactionProofsEvent {
    public TransactionProofsRefusedEvent(Wallet wallet, Set<BlockTransactionHash> references) {
        super(wallet, references);
    }
}
