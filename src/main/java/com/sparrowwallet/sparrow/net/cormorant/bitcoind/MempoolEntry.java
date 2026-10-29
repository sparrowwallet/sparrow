package com.sparrowwallet.sparrow.net.cormorant.bitcoind;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.sparrowwallet.sparrow.net.cormorant.index.TxEntry;

//Bitcoin Core v32.1 deprecates vsize in favour of the identical vsize_adjusted, so prefer the latter and fall back for earlier versions
@JsonIgnoreProperties(ignoreUnknown = true)
public record MempoolEntry(Integer vsize, Integer vsize_adjusted, int ancestorsize, FeesMempoolEntry fees) {
    public int getVsize() {
        return vsize_adjusted != null ? vsize_adjusted : vsize;
    }

    public boolean hasUnconfirmedParents() {
        return getVsize() != ancestorsize;
    }

    public TxEntry getTxEntry(String txid) {
        return new TxEntry(hasUnconfirmedParents() ? -1 : 0, 0, txid, fees().base());
    }

    public VsizeFeerate getVsizeFeerate() {
        return new VsizeFeerate(getVsize(), fees().base());
    }
}
