package com.sparrowwallet.sparrow;

import com.sparrowwallet.drongo.protocol.BlockHeader;

/**
 * The height and header of a chain tip, carried together so that a reader cannot take the height of one block with the header of another.
 * Whether a tip is one a server has announced or one that has been verified is said by the accessor or the field holding it, not by this type.
 */
public record ChainTip(int height, BlockHeader header) {}
