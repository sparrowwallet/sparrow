package com.sparrowwallet.sparrow.net.cormorant.bitcoind;

import java.util.Date;

public class ScanDateBeforePruneException extends Exception {
    private final Date rescanSince;
    private final Date prunedDate;

    public ScanDateBeforePruneException(Date rescanSince, Date prunedDate) {
        this.rescanSince = rescanSince;
        this.prunedDate = prunedDate;
    }

    public Date getRescanSince() {
        return rescanSince;
    }

    public Date getPrunedDate() {
        return prunedDate;
    }
}
