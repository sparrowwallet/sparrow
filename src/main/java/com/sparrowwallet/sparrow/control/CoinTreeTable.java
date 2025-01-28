package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.drongo.BitcoinUnit;
import com.sparrowwallet.drongo.wallet.TableType;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.drongo.wallet.WalletTable;
import com.sparrowwallet.sparrow.CurrencyRate;
import com.sparrowwallet.sparrow.UnitFormat;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.event.WalletTableColumnsResizedEvent;
import com.sparrowwallet.sparrow.event.WalletAddressesChangedEvent;
import com.sparrowwallet.sparrow.event.WalletDataChangedEvent;
import com.sparrowwallet.sparrow.event.WalletHistoryStatusEvent;
import com.sparrowwallet.sparrow.io.Config;
import com.sparrowwallet.sparrow.io.Storage;
import com.sparrowwallet.sparrow.net.ServerType;
import com.sparrowwallet.sparrow.wallet.Entry;
import io.reactivex.Observable;
import io.reactivex.subjects.PublishSubject;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

public class CoinTreeTable extends TreeTableView<Entry> {
    private BitcoinUnit bitcoinUnit;
    private UnitFormat unitFormat;
    private CurrencyRate currencyRate;
    protected static final double STANDARD_WIDTH = 100.0;

    private final PublishSubject<WalletTableColumnsResizedEvent> columnResizeSubject = PublishSubject.create();
    private final Observable<WalletTableColumnsResizedEvent> columnResizeEvents = columnResizeSubject.debounce(1, TimeUnit.SECONDS);

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

    @SuppressWarnings("deprecation")
    protected void setupColumnWidths(TableType tableType) {
        Double[] savedWidths = getSavedColumnWidths(tableType);
        for(int i = 0; i < getColumns().size(); i++) {
            TreeTableColumn<Entry, ?> column = getColumns().get(i);
            column.setPrefWidth(savedWidths != null && getColumns().size() == savedWidths.length ? savedWidths[i] : STANDARD_WIDTH);
        }

        //TODO: Replace with TreeTableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN when JavaFX 20+ has headless support
        setColumnResizePolicy(TreeTableView.CONSTRAINED_RESIZE_POLICY);

        getColumns().getLast().widthProperty().addListener((_, _, _) -> {
            if(getRoot() != null && getRoot().getValue() != null && getRoot().getValue().getWallet() != null) {
                Double[] widths = getColumns().stream().map(TableColumnBase::getWidth).toArray(Double[]::new);
                WalletTable walletTable = new WalletTable(tableType, widths);
                columnResizeSubject.onNext(new WalletTableColumnsResizedEvent(getRoot().getValue().getWallet(), walletTable));
            }
        });

        columnResizeEvents.skip(3, TimeUnit.SECONDS).subscribe(event -> EventManager.get().post(event));
    }

    private Double[] getSavedColumnWidths(TableType tableType) {
        if(getRoot() != null && getRoot().getValue() != null && getRoot().getValue().getWallet() != null) {
            Wallet wallet = getRoot().getValue().getWallet();
            WalletTable walletTable = wallet.getWalletTable(tableType);
            if(walletTable != null) {
                return walletTable.getWidths();
            }
        }

        return null;
    }
}
