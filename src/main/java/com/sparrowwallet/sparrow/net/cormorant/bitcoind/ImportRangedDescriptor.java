package com.sparrowwallet.sparrow.net.cormorant.bitcoind;

public class ImportRangedDescriptor extends ImportDescriptor {
    private final Integer range;

    public ImportRangedDescriptor(String desc, Boolean active, Integer range, Object timestamp, Boolean internal) {
        super(desc, active, timestamp, internal);
        this.range = range;
    }

    public Integer getRange() {
        return range;
    }
}
