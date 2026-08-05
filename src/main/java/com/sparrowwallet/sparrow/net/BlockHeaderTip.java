package com.sparrowwallet.sparrow.net;

import com.sparrowwallet.drongo.Utils;
import com.sparrowwallet.drongo.protocol.BlockHeader;
import com.sparrowwallet.drongo.protocol.Sha256Hash;

public class BlockHeaderTip {
    public int height;
    public String hex;

    private volatile BlockHeader blockHeader;

    public BlockHeader getBlockHeader() {
        if(blockHeader == null) {
            if(hex == null) {
                blockHeader = new BlockHeader(0, Sha256Hash.ZERO_HASH, Sha256Hash.ZERO_HASH, Sha256Hash.ZERO_HASH, 0, 0, 0);
            } else {
                blockHeader = new BlockHeader(Utils.hexToBytes(hex));
            }
        }

        return blockHeader;
    }
}
