package com.sparrowwallet.sparrow.net.cormorant.index;

public class TxEntry implements Comparable<TxEntry> {
    public final int height;
    private final transient int index;
    public final String tx_hash;

    public TxEntry(int height, int index, String tx_hash) {
        this.height = height;
        this.index = index;
        this.tx_hash = tx_hash;
    }

    @Override
    public boolean equals(Object o) {
        if(this == o) {
            return true;
        }
        if(o == null || getClass() != o.getClass()) {
            return false;
        }

        TxEntry txEntry = (TxEntry) o;

        if(height != txEntry.height) {
            return false;
        }
        return tx_hash.equals(txEntry.tx_hash);
    }

    @Override
    public int hashCode() {
        int result = height;
        result = 31 * result + tx_hash.hashCode();
        return result;
    }

    @Override
    public int compareTo(TxEntry o) {
        if(height <= 0 && o.height > 0) {
            return 1;
        }

        if(height > 0 && o.height <= 0) {
            return -1;
        }

        if(height != o.height) {
            return height - o.height;
        }

        if(height <= 0) {
            return tx_hash.compareTo(o.tx_hash);
        }

        return index - o.index;
    }

    @Override
    public String toString() {
        return "TxEntry{" +
                "height=" + height +
                ", index=" + index +
                ", tx_hash='" + tx_hash + '\'' +
                '}';
    }
}
