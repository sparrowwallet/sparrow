package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.drongo.wallet.Wallet;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

public class CormorantPruneStatusEvent extends CormorantStatusEvent {
    private static final DateFormat DATE_FORMAT = new SimpleDateFormat("yyyy/MM/dd");

    private final Wallet wallet;
    private final Date scanDate;
    private final Date pruneDate;
    private final boolean legacyWalletExists;

    public CormorantPruneStatusEvent(String status, Wallet wallet, Date scanDate, Date pruneDate, boolean legacyWalletExists) {
        super(status);
        this.wallet = wallet;
        this.scanDate = scanDate;
        this.pruneDate = pruneDate;
        this.legacyWalletExists = legacyWalletExists;
    }

    @Override
    public boolean isFor(Wallet wallet) {
        return this.wallet == wallet;
    }

    public Wallet getWallet() {
        return wallet;
    }

    public Date getScanDate() {
        return scanDate;
    }

    public String getScanDateAsString() {
        return DATE_FORMAT.format(scanDate);
    }

    public Date getPruneDate() {
        return pruneDate;
    }

    public String getPruneDateAsString() {
        return DATE_FORMAT.format(pruneDate);
    }

    public boolean legacyWalletExists() {
        return legacyWalletExists;
    }
}
