package com.sparrowwallet.sparrow.net.cormorant.bitcoind;

import java.util.Date;

public class ScanDateBeforePruneException extends Exception {
    private final String descriptor;
    private final Date rescanSince;
    private final Date prunedDate;

    public ScanDateBeforePruneException(String descriptor, Date rescanSince, Date prunedDate) {
        this.descriptor = descriptor;
        this.rescanSince = rescanSince;
        this.prunedDate = prunedDate;
    }

    public String getDescriptor() {
        return descriptor;
    }

    public Date getRescanSince() {
        return rescanSince;
    }

    public Date getPrunedDate() {
        return prunedDate;
    }
}
