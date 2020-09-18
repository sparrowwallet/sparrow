package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.drongo.BitcoinUnit;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.event.WalletHistoryStatusEvent;
import com.sparrowwallet.sparrow.io.Config;
import com.sparrowwallet.sparrow.wallet.Entry;
import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.scene.control.TreeTableView;

public class CoinTreeTable extends TreeTableView<Entry> {
    private BitcoinUnit bitcoinUnit;

    public BitcoinUnit getBitcoinUnit() {
        return bitcoinUnit;
    }

    public void setBitcoinUnit(BitcoinUnit bitcoinUnit) {
        this.bitcoinUnit = bitcoinUnit;
    }

    public void setBitcoinUnit(Wallet wallet) {
        setBitcoinUnit(wallet, Config.get().getBitcoinUnit());
    }

    public void setBitcoinUnit(Wallet wallet, BitcoinUnit unit) {
        if(unit == null || unit.equals(BitcoinUnit.AUTO)) {
            unit = wallet.getAutoUnit();
        }

        boolean changed = (bitcoinUnit != unit);
        this.bitcoinUnit = unit;

        if(changed && !getChildren().isEmpty()) {
            refresh();
        }
    }

    public void updateHistoryStatus(WalletHistoryStatusEvent event) {
        Platform.runLater(() -> {
            if(event.getErrorMessage() != null) {
                setPlaceholder(new Label("Error loading transactions: " + event.getErrorMessage()));
            } else if(event.isLoading()) {
                if(event.getStatusMessage() != null) {
                    setPlaceholder(new Label(event.getStatusMessage() + "..."));
                } else {
                    setPlaceholder(new Label("Loading transactions..."));
                }
            } else {
                setPlaceholder(new Label("No transactions"));
            }
        });
    }
}
