package com.sparrowwallet.sparrow.net.cormorant.bitcoind;

import com.sparrowwallet.drongo.protocol.Transaction;

public class VsizeFeerate implements Comparable<VsizeFeerate> {
    private final int vsize;
    private final float feerate;

    public VsizeFeerate(int vsize, double fee) {
        this.vsize = vsize;
        double feeRate = fee / vsize * Transaction.SATOSHIS_PER_BITCOIN;
        //Round down to 0.1 sats/vb precision
        this.feerate = (float) (Math.floor(10 * feeRate) / 10);
    }

    public int getVsize() {
        return vsize;
    }

    public double getFeerate() {
        return feerate;
    }

    @Override
    public int compareTo(VsizeFeerate o) {
        return Float.compare(o.feerate, feerate);
    }
}
