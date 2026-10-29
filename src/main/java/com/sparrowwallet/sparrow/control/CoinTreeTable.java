package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.drongo.BitcoinUnit;
import com.sparrowwallet.drongo.OsType;
import com.sparrowwallet.drongo.policy.PolicyType;
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
import com.sparrowwallet.sparrow.net.ElectrumServer;
import com.sparrowwallet.sparrow.net.ServerType;
import com.sparrowwallet.sparrow.net.cormorant.Cormorant;
import com.sparrowwallet.sparrow.net.cormorant.bitcoind.BitcoindClient;
import com.sparrowwallet.sparrow.wallet.Entry;
import io.reactivex.Observable;
import io.reactivex.subjects.PublishSubject;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.AccessibleAttribute;
import javafx.scene.AccessibleRole;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.StackPane;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class CoinTreeTable extends TreeTableView<Entry> {
    private TableType tableType;
    private BitcoinUnit bitcoinUnit;
    private UnitFormat unitFormat;
    private CurrencyRate currencyRate;
    protected static final double STANDARD_WIDTH = 100.0;

    private final PublishSubject<WalletTableChangedEvent> walletTableSubject = PublishSubject.create();
    private final Observable<WalletTableChangedEvent> walletTableEvents = walletTableSubject.debounce(1, TimeUnit.SECONDS);

    public CoinTreeTable() {
        setRowFactory(_ -> new TreeTableRow<>() {
            @Override
            public Object queryAccessibleAttribute(AccessibleAttribute attribute, Object... parameters) {
                //Empty rows fill the space below the last entry and have nothing to read
                if(isEmpty()) {
                    if(attribute == AccessibleAttribute.ROLE) {
                        return AccessibleRole.NODE;
                    } else if(attribute == AccessibleAttribute.CHILDREN) {
                        return FXCollections.emptyObservableList();
                    }
                }

                return super.queryAccessibleAttribute(attribute, parameters);
            }
        });

        //Row actions are in the context menu of each row's entry cell, which a keyboard menu request to the table opens for the focused row.
        //Only Windows sends Shift-F10 and the Menu key as that request; Linux and macOS send it only for a right click, so those keys are handled here.
        //macOS keyboards have no Menu key, so Control-Return is also handled there.
        boolean macOS = OsType.getCurrent() == OsType.MACOS;
        if(OsType.getCurrent() != OsType.WINDOWS) {
            addEventHandler(KeyEvent.KEY_PRESSED, event -> {
                boolean menuKey = event.getCode() == KeyCode.CONTEXT_MENU || (event.getCode() == KeyCode.F10 && event.isShiftDown())
                        || (macOS && event.getCode() == KeyCode.ENTER && event.isControlDown() && !event.isShiftDown() && !event.isAltDown() && !event.isMetaDown());
                if(menuKey && event.getTarget() == this && showRowMenu(getFocusModel().getFocusedIndex())) {
                    event.consume();
                }
            });
        }
        addEventHandler(ContextMenuEvent.CONTEXT_MENU_REQUESTED, event -> {
            if(event.isKeyboardTrigger() && event.getTarget() == this && showRowMenu(getFocusModel().getFocusedIndex())) {
                event.consume();
            }
        });
    }

    //Screen readers read the help of a row's focused cell, so the key that opens the row's actions follows the cell's own help
    static Object withRowActionsHelp(Object help) {
        String rowActionsHelp = OsType.getCurrent() == OsType.MACOS ? "Press Shift-F10 or Control-Return for row actions" : "Press Shift-F10 for row actions";
        return help == null || help.toString().isEmpty() ? rowActionsHelp : help + ". " + rowActionsHelp;
    }

    //Moving up and down a table reads the first cell of each row, so a first cell holding only a name or type also reads the rest of its row.
    //On macOS its own text is left out, as VoiceOver also reads the text inside the cell; other screen readers read only the name.
    static String getRowSummary(TreeTableCell<?, ?> cell) {
        if(cell.getText() == null || cell.isEditing() || cell.getTableRow() == null) {
            return null;
        }

        boolean ownTextReadSeparately = OsType.getCurrent() == OsType.MACOS;
        String summary = cell.getTableRow().getChildrenUnmodifiable().stream().filter(node -> node instanceof TreeTableCell<?, ?> && !(ownTextReadSeparately && node == cell))
                .map(node -> ((TreeTableCell<?, ?>)node).getText()).filter(text -> text != null && !text.isEmpty()).collect(Collectors.joining(", "));
        return summary.isEmpty() ? null : summary;
    }

    private boolean showRowMenu(int index) {
        //Some tables have no row menus (wallet summary, Whirlpool child wallet addresses), and there the keys leave the table as it is
        if(getVisibleRows().stream().noneMatch(row -> getMenuCell(row) != null)) {
            return false;
        }

        //Tabbing into a table focuses no row, so the menu is for the first row, which is then focused to show which row the menu is for
        int rowIndex = Math.max(index, 0);
        TreeTableRow<?> row = getVisibleRow(rowIndex);
        if(row == null) {
            //The focused row may have been scrolled out of view with the mouse or trackpad, and only rows on screen have cells
            scrollTo(rowIndex);
            layout();
            row = getVisibleRow(rowIndex);
        }

        EntryCell menuCell = row == null ? null : getMenuCell(row);
        if(menuCell == null) {
            return false;
        }

        if(index < 0) {
            getFocusModel().focus(rowIndex);
        }
        menuCell.getContextMenu().show(menuCell, Side.BOTTOM, 0, 0);
        return true;
    }

    private List<TreeTableRow<?>> getVisibleRows() {
        List<TreeTableRow<?>> rows = new ArrayList<>();
        for(Node node : lookupAll(".tree-table-row-cell")) {
            if(node instanceof TreeTableRow<?> row && row.isVisible() && !row.isEmpty()) {
                rows.add(row);
            }
        }

        return rows;
    }

    private TreeTableRow<?> getVisibleRow(int index) {
        return getVisibleRows().stream().filter(row -> row.getIndex() == index).findFirst().orElse(null);
    }

    private static EntryCell getMenuCell(TreeTableRow<?> row) {
        for(Node child : row.getChildrenUnmodifiable()) {
            if(child instanceof EntryCell entryCell && entryCell.getContextMenu() != null) {
                return entryCell;
            }
        }

        return null;
    }

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
                            setPlaceholder(new Label(event.getStatusMessage() + (event.getStatusMessage().contains("...") ? "" : "...")));
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

        if((Config.get().getServerType() == ServerType.BITCOIN_CORE || wallet.getPolicyType() == PolicyType.SINGLE_SP) && !AppServices.isConnecting() && !isFullyScanned(wallet)) {
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
                    wallet.setBirthHeight(null);
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
        } else if(!AppServices.isConnecting() && Config.get().getServerType() == ServerType.BITCOIN_CORE && isFullyScanned(wallet)) {
            Date prunedDate = getPrunedDate();
            if(prunedDate != null) {
                DateFormat dateFormat = new SimpleDateFormat(DateStringConverter.FORMAT_PATTERN);
                Label prunedLabel = new Label("Scanned to pruned start date of " + dateFormat.format(prunedDate));
                prunedLabel.setTranslateY(30);
                stackPane.getChildren().add(prunedLabel);
            }
        }

        stackPane.setAlignment(Pos.CENTER);
        return stackPane;
    }

    private boolean isFullyScanned(Wallet wallet) {
        if(wallet.getPolicyType() == PolicyType.SINGLE_SP) {
            return wallet.isValid() && ElectrumServer.isSilentPaymentsFullyCovered(wallet.getSilentPaymentScanAddress());
        }

        if(Config.get().getServerType() == ServerType.BITCOIN_CORE) {
            Date prunedDate = getPrunedDate();
            return prunedDate != null && wallet.getBirthDate() != null && !wallet.getBirthDate().after(prunedDate);
        }

        return false;
    }

    private static Date getPrunedDate() {
        Cormorant cormorant = ElectrumServer.getCormorant();
        if(cormorant == null) {
            return null;
        }

        BitcoindClient bitcoindClient = cormorant.getBitcoindClient();
        if(bitcoindClient == null || !bitcoindClient.isPruned()) {
            return null;
        }

        return bitcoindClient.getCachedPrunedDate();
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

    protected void setupColumnWidths() {
        Double[] savedWidths = getSavedColumnWidths();
        for(int i = 0; i < getColumns().size(); i++) {
            TreeTableColumn<Entry, ?> column = getColumns().get(i);
            column.setPrefWidth(savedWidths != null && getColumns().size() == savedWidths.length ? savedWidths[i] : STANDARD_WIDTH);
        }

        setColumnResizePolicy(TreeTableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

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
