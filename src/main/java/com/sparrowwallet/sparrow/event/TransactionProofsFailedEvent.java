package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.drongo.wallet.BlockTransactionHash;
import com.sparrowwallet.drongo.wallet.Wallet;

import java.util.Set;

/**
 * Posted once per wallet history pass where the server supplied a proof that did not reconstruct the merkle root of the verified header at the height
 * it reported - the server proven wrong rather than merely unhelpful. The transactions carry the reported heights, and are already written unconfirmed.
 * <p>
 * Dispatched on the wallet history thread, so a handler must hop to the application thread itself.
 */
public class TransactionProofsFailedEvent extends TransactionProofsEvent {
    public TransactionProofsFailedEvent(Wallet wallet, Set<BlockTransactionHash> references) {
        super(wallet, references);
    }
}
