package com.sparrowwallet.sparrow.net;

import com.sparrowwallet.drongo.Utils;
import com.sparrowwallet.drongo.protocol.BlockHeader;

class BlockHeaderTip {
    public int height;
    public String hex;

    public BlockHeader getBlockHeader() {
        if(hex == null) {
            return null;
        }

        byte[] blockHeaderBytes = Utils.hexToBytes(hex);
        return new BlockHeader(blockHeaderBytes);
    }
}
