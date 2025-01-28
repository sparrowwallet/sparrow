package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.drongo.wallet.TableType;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.drongo.wallet.WalletTable;

public class WalletTableColumnsResizedEvent extends WalletChangedEvent {
    private final WalletTable walletTable;

    public WalletTableColumnsResizedEvent(Wallet wallet, WalletTable walletTable) {
        super(wallet);
        this.walletTable = walletTable;
    }

    public WalletTable getWalletTable() {
        return walletTable;
    }

    public TableType getTableType() {
        return walletTable == null ? null : walletTable.getTableType();
    }
}
