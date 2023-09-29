package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.drongo.BitcoinUnit;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.CurrencyRate;
import com.sparrowwallet.sparrow.UnitFormat;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.event.WalletAddressesChangedEvent;
import com.sparrowwallet.sparrow.event.WalletDataChangedEvent;
import com.sparrowwallet.sparrow.event.WalletHistoryStatusEvent;
import com.sparrowwallet.sparrow.io.Config;
import com.sparrowwallet.sparrow.io.Storage;
import com.sparrowwallet.sparrow.net.ServerType;
import com.sparrowwallet.sparrow.wallet.Entry;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.TreeTableColumn;
import javafx.scene.control.TreeTableView;
import javafx.scene.layout.StackPane;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Optional;

public class CoinTreeTable extends TreeTableView<Entry> {
    private BitcoinUnit bitcoinUnit;
    private UnitFormat unitFormat;
    private CurrencyRate currencyRate;

    public BitcoinUnit getBitcoinUnit() {
        return bitcoinUnit;
    }

    public UnitFormat getUnitFormat() {
        return unitFormat;
    }

    public void setUnitFormat(Wallet wallet) {
        setUnitFormat(wallet, Config.get().getUnitFormat(), Config.get().getBitcoinUnit());
    }

    public void setUnitFormat(Wallet wallet, UnitFormat format) {
        setUnitFormat(wallet, format, Config.get().getBitcoinUnit());
    }

    public void setUnitFormat(Wallet wallet, UnitFormat format, BitcoinUnit unit) {
        if(format == null) {
            format = UnitFormat.DOT;
        }

        if(unit == null || unit.equals(BitcoinUnit.AUTO)) {
            unit = wallet.getAutoUnit();
        }

        boolean changed = (unitFormat != format);
        changed |= (bitcoinUnit != unit);
        this.unitFormat = format;
        this.bitcoinUnit = unit;

        if(changed && !getChildren().isEmpty()) {
            refresh();
        }
    }

    public CurrencyRate getCurrencyRate() {
        return currencyRate;
    }

    public void setCurrencyRate(CurrencyRate currencyRate) {
        this.currencyRate = currencyRate;

        if(!getChildren().isEmpty()) {
            refresh();
        }
    }

    public void updateHistoryStatus(WalletHistoryStatusEvent event) {
        if(getRoot() != null) {
            Entry entry = getRoot().getValue();
            if(entry != null && event.getWallet() != null && entry.getWallet() == event.getWallet()) {
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
                        setPlaceholder(getDefaultPlaceholder(event.getWallet()));
                    }
                });
            }
        }
    }

    protected Node getDefaultPlaceholder(Wallet wallet) {
        StackPane stackPane = new StackPane();
        stackPane.getChildren().add(AppServices.isConnecting() ? new Label("Loading transactions...") : new Label("No transactions"));

        if(Config.get().getServerType() == ServerType.BITCOIN_CORE && !AppServices.isConnecting()) {
            Hyperlink hyperlink = new Hyperlink();
            hyperlink.setTranslateY(30);
            hyperlink.setOnAction(event -> {
                WalletBirthDateDialog dlg = new WalletBirthDateDialog(wallet.getBirthDate(), false);
                dlg.initOwner(this.getScene().getWindow());
                Optional<Date> optDate = dlg.showAndWait();
                if(optDate.isPresent()) {
                    Storage storage = AppServices.get().getOpenWallets().get(wallet);
                    Wallet pastWallet = wallet.copy();
                    wallet.setBirthDate(optDate.get());
                    //Trigger background save of birthdate
                    EventManager.get().post(new WalletDataChangedEvent(wallet));
                    //Trigger full wallet rescan
                    wallet.clearHistory();
                    EventManager.get().post(new WalletAddressesChangedEvent(wallet, pastWallet, storage.getWalletId(wallet)));
                }
            });
            if(wallet.getBirthDate() == null) {
                hyperlink.setText("Scan for previous transactions?");
            } else {
                DateFormat dateFormat = new SimpleDateFormat(DateStringConverter.FORMAT_PATTERN);
                hyperlink.setText("Scan for transactions earlier than " + dateFormat.format(wallet.getBirthDate()) + "?");
            }

            stackPane.getChildren().add(hyperlink);
        }

        stackPane.setAlignment(Pos.CENTER);
        return stackPane;
    }

    public void setSortColumn(int columnIndex, TreeTableColumn.SortType sortType) {
        if(columnIndex >= 0 && columnIndex < getColumns().size() && getSortOrder().isEmpty() && !getRoot().getChildren().isEmpty()) {
            TreeTableColumn<Entry, ?> column = getColumns().get(columnIndex);
            column.setSortType(sortType == null ? TreeTableColumn.SortType.DESCENDING : sortType);
            getSortOrder().add(column);
        }
    }
}
