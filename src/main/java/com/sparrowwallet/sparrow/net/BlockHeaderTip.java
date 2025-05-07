package com.sparrowwallet.sparrow.net;

import com.sparrowwallet.drongo.Utils;
import com.sparrowwallet.drongo.protocol.BlockHeader;
import com.sparrowwallet.drongo.protocol.Sha256Hash;

public class BlockHeaderTip {
    public int height;
    public String hex;

    public BlockHeader getBlockHeader() {
        if(hex == null) {
            return new BlockHeader(0, Sha256Hash.ZERO_HASH, Sha256Hash.ZERO_HASH, Sha256Hash.ZERO_HASH, 0, 0, 0);
        }

        byte[] blockHeaderBytes = Utils.hexToBytes(hex);
        return new BlockHeader(blockHeaderBytes);
    }
}
