package com.sparrowwallet.sparrow.net.cormorant.bitcoind;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.sparrowwallet.sparrow.net.cormorant.electrum.ElectrumBlockHeader;
import com.sparrowwallet.drongo.protocol.BlockHeader;
import com.sparrowwallet.drongo.protocol.Sha256Hash;
import com.sparrowwallet.drongo.Utils;

import java.math.BigInteger;

@JsonIgnoreProperties(ignoreUnknown = true)
public record VerboseBlockHeader(String hash, int confirmations, int height, int version, String versionHex, String merkleroot, long time, long mediantime, long nonce,
                                 String bits, double difficulty, String chainwork, int nTx, String previousblockhash) {
    public ElectrumBlockHeader getBlockHeader() {
        BigInteger nBits = new BigInteger(bits, 16);
        BlockHeader blockHeader = new BlockHeader(version, previousblockhash == null ? Sha256Hash.ZERO_HASH : Sha256Hash.wrap(previousblockhash), Sha256Hash.wrap(merkleroot), null, time, nBits.longValue(), nonce);
        return new ElectrumBlockHeader(height, Utils.bytesToHex(blockHeader.bitcoinSerialize()));
    }
}
