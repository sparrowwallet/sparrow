package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.drongo.protocol.BlockHeader;

public class NewBlockEvent {
    private final int height;
    private final BlockHeader blockHeader;

    public NewBlockEvent(int height, BlockHeader blockHeader) {
        this.height = height;
        this.blockHeader = blockHeader;
    }

    public int getHeight() {
        return height;
    }

    public BlockHeader getBlockHeader() {
        return blockHeader;
    }
}
