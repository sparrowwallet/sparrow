package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.drongo.BitcoinUnit;
import com.sparrowwallet.drongo.wallet.SortDirection;
import com.sparrowwallet.drongo.wallet.TableType;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.drongo.wallet.WalletTable;
import com.sparrowwallet.sparrow.CurrencyRate;
import com.sparrowwallet.sparrow.UnitFormat;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.event.WalletTableChangedEvent;
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
import javafx.collections.ListChangeListener;
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
    private TableType tableType;
    private BitcoinUnit bitcoinUnit;
    private UnitFormat unitFormat;
    private CurrencyRate currencyRate;
    protected static final double STANDARD_WIDTH = 100.0;

    private final PublishSubject<WalletTableChangedEvent> walletTableSubject = PublishSubject.create();
    private final Observable<WalletTableChangedEvent> walletTableEvents = walletTableSubject.debounce(1, TimeUnit.SECONDS);

    public TableType getTableType() {
        return tableType;
    }

    public void setTableType(TableType tableType) {
        this.tableType = tableType;
    }

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

    protected void setupColumnSort(int defaultColumnIndex, TreeTableColumn.SortType defaultSortType) {
        WalletTable.Sort columnSort = getSavedColumnSort();
        if(columnSort == null) {
            columnSort = new WalletTable.Sort(defaultColumnIndex, getSortDirection(defaultSortType));
        }

        setSortColumn(columnSort);

        getSortOrder().addListener((ListChangeListener<? super TreeTableColumn<Entry, ?>>) c -> {
            if(c.next()) {
                walletTableChanged();
            }
        });
        for(TreeTableColumn<Entry, ?> column : getColumns()) {
            column.sortTypeProperty().addListener((_, _, _) -> walletTableChanged());
        }
    }

    protected void resetSortColumn() {
        setSortColumn(getColumnSort());
    }

    protected void setSortColumn(WalletTable.Sort sort) {
        if(sort.sortColumn() >= 0 && sort.sortColumn() < getColumns().size() && getSortOrder().isEmpty() && !getRoot().getChildren().isEmpty()) {
            TreeTableColumn<Entry, ?> column = getColumns().get(sort.sortColumn());
            column.setSortType(sort.sortDirection() == SortDirection.DESCENDING ? TreeTableColumn.SortType.DESCENDING : TreeTableColumn.SortType.ASCENDING);
            getSortOrder().add(column);
        }
    }

    private WalletTable.Sort getColumnSort() {
        if(getSortOrder().isEmpty() || !getColumns().contains(getSortOrder().getFirst())) {
            return new WalletTable.Sort(tableType == TableType.UTXOS ? getColumns().size() - 1 : 0, SortDirection.DESCENDING);
        }

        return new WalletTable.Sort(getColumns().indexOf(getSortOrder().getFirst()), getSortDirection(getSortOrder().getFirst().getSortType()));
    }

    private SortDirection getSortDirection(TreeTableColumn.SortType sortType) {
        return sortType == TreeTableColumn.SortType.ASCENDING ? SortDirection.ASCENDING : SortDirection.DESCENDING;
    }

    private WalletTable.Sort getSavedColumnSort() {
        if(getRoot() != null && getRoot().getValue() != null && getRoot().getValue().getWallet() != null) {
            Wallet wallet = getRoot().getValue().getWallet();
            WalletTable walletTable = wallet.getWalletTable(tableType);
            if(walletTable != null) {
                return walletTable.getSort();
            }
        }

        return null;
    }

    @SuppressWarnings("deprecation")
    protected void setupColumnWidths() {
        Double[] savedWidths = getSavedColumnWidths();
        for(int i = 0; i < getColumns().size(); i++) {
            TreeTableColumn<Entry, ?> column = getColumns().get(i);
            column.setPrefWidth(savedWidths != null && getColumns().size() == savedWidths.length ? savedWidths[i] : STANDARD_WIDTH);
        }

        //TODO: Replace with TreeTableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN when JavaFX 20+ has headless support
        setColumnResizePolicy(TreeTableView.CONSTRAINED_RESIZE_POLICY);

        getColumns().getLast().widthProperty().addListener((_, _, _) -> walletTableChanged());

        //Ignore initial resizes during layout
        walletTableEvents.skip(3, TimeUnit.SECONDS).subscribe(event -> {
            event.getWallet().getWalletTables().put(event.getTableType(), event.getWalletTable());
            EventManager.get().post(event);

            //Reset pref widths here so window resizes don't cause reversion to previously set pref widths
            Double[] widths = event.getWalletTable().getWidths();
            for(int i = 0; i < getColumns().size(); i++) {
                TreeTableColumn<Entry, ?> column = getColumns().get(i);
                column.setPrefWidth(widths != null && getColumns().size() == widths.length ? widths[i] : STANDARD_WIDTH);
            }
        });
    }

    private void walletTableChanged() {
        if(getRoot() != null && getRoot().getValue() != null && getRoot().getValue().getWallet() != null) {
            WalletTable walletTable = new WalletTable(tableType, getColumnWidths(), getColumnSort());
            walletTableSubject.onNext(new WalletTableChangedEvent(getRoot().getValue().getWallet(), walletTable));
        }
    }

    private Double[] getColumnWidths() {
        return getColumns().stream().map(TableColumnBase::getWidth).toArray(Double[]::new);
    }

    private Double[] getSavedColumnWidths() {
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
