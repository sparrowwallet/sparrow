package com.sparrowwallet.sparrow;

import com.sparrowwallet.drongo.protocol.BlockHeader;

/**
 * The height and header of a chain tip, carried together so that a reader cannot take the height of one block with the header of another.
 * Whether a tip is one a server has announced or one that has been verified is said by the accessor or the field holding it, not by this type.
 */
public record ChainTip(int height, BlockHeader header) {
    /**
     * Whether the other tip is the same block, announced again or not. The header has no value equality of its own, so a repeated announcement of
     * a block is a different object that only its height and hash identify.
     */
    public boolean isSameBlock(ChainTip other) {
        return this == other || (other != null && height == other.height && header != null && other.header != null && header.getHash().equals(other.header.getHash()));
    }
}
