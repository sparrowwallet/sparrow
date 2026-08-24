package com.sparrowwallet.sparrow.net;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * The blockchain.block.headers response: a run of consecutive block headers as concatenated hex, with the maximum number of headers the server will return.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class BlockHeaders {
    public static final int HEADER_HEX_LENGTH = 160;

    /**
     * Substituted for a range the server returned an error for, and filtered out before a batched result is returned.
     */
    public static final BlockHeaders ERROR_HEADERS = new BlockHeaders();

    public int count;
    public String hex;
    public int max;

    @Override
    public String toString() {
        return "BlockHeaders{count=" + count + ", max=" + max + '}';
    }
}
