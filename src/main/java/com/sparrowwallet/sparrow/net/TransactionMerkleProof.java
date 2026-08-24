package com.sparrowwallet.sparrow.net;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * The blockchain.transaction.get_merkle response: the sibling path from a transaction to the merkle root of the block containing it.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class TransactionMerkleProof {
    /**
     * Substituted for a transaction the server returned an error for, so that a batch covering many transactions can partially succeed.
     */
    public static final TransactionMerkleProof ERROR_PROOF = new TransactionMerkleProof();

    public int block_height;
    public List<String> merkle;
    public int pos;

    @Override
    public String toString() {
        return "TransactionMerkleProof{block_height=" + block_height + ", pos=" + pos + ", merkle=" + merkle + '}';
    }
}
