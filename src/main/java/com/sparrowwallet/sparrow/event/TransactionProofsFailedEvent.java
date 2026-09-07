package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.drongo.wallet.BlockTransactionHash;
import com.sparrowwallet.drongo.wallet.Wallet;

import java.util.Set;

/**
 * Posted where the server supplied a proof that did not reconstruct the merkle root of the verified header at the height it reported - the server
 * proven wrong rather than merely unhelpful. Once per wallet history pass, or once for a transaction the transaction tab asked about. The transactions
 * carry the reported heights, and what a wallet holds is already written unconfirmed.
 * <p>
 * Dispatched on the thread that asked for the proof, so a handler must hop to the application thread itself.
 */
public class TransactionProofsFailedEvent extends TransactionProofsEvent {
    public TransactionProofsFailedEvent(Wallet wallet, Set<BlockTransactionHash> references) {
        super(wallet, references);
    }
}
