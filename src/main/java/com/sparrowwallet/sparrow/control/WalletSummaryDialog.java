package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.drongo.wallet.TableType;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.CurrencyRate;
import com.sparrowwallet.sparrow.io.Config;
import com.sparrowwallet.sparrow.net.ExchangeSource;
import com.sparrowwallet.sparrow.wallet.Entry;
import com.sparrowwallet.sparrow.wallet.Function;
import com.sparrowwallet.sparrow.wallet.WalletForm;
import com.sparrowwallet.sparrow.wallet.WalletTransactionsEntry;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.geometry.Side;
import javafx.scene.chart.NumberAxis;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class WalletSummaryDialog extends Dialog<Void> {
    public WalletSummaryDialog(List<List<WalletForm>> summaryWalletFormsList) {
        List<List<WalletForm>> walletFormsList = new ArrayList<>(summaryWalletFormsList);
        walletFormsList.removeIf(List::isEmpty);
        if(walletFormsList.isEmpty()) {
            throw new IllegalArgumentException("No wallets selected to summarize");
        }

        boolean allOpenWallets = walletFormsList.size() > 1;
        List<Wallet> masterWallets = walletFormsList.stream().map(walletForms -> walletForms.get(0).getMasterWallet()).toList();

        final DialogPane dialogPane = getDialogPane();
        dialogPane.getStylesheets().add(AppServices.class.getResource("general.css").toExternalForm());
        dialogPane.getStylesheets().add(AppServices.class.getResource("dialog.css").toExternalForm());
        dialogPane.getStylesheets().add(AppServices.class.getResource("wallet/wallet.css").toExternalForm());
        dialogPane.getStylesheets().add(AppServices.class.getResource("wallet/transactions.css").toExternalForm());

        AppServices.setStageIcon(dialogPane.getScene().getWindow());
        dialogPane.setHeaderText("Wallet Summary for " + (allOpenWallets ? "All Open Wallets" : masterWallets.get(0).getName()));

        Image image = new Image("image/sparrow-small.png", 50, 50, false, false);
        if(!image.isError()) {
            ImageView imageView = new ImageView();
            imageView.setSmooth(false);
            imageView.setImage(image);
            dialogPane.setGraphic(imageView);
        }

        HBox hBox = new HBox(40);

        CoinTreeTable table = new CoinTreeTable();
        table.setTableType(TableType.WALLET_SUMMARY);

        TreeTableColumn<Entry, String> nameColumn = new TreeTableColumn<>("Wallet");
        nameColumn.setCellValueFactory((TreeTableColumn.CellDataFeatures<Entry, String> param) -> {
            return new ReadOnlyObjectWrapper<>(param.getValue().getValue().getLabel());
        });
        nameColumn.setCellFactory(p -> new LabelCell());
        table.getColumns().add(nameColumn);

        TreeTableColumn<Entry, Number> balanceColumn = new TreeTableColumn<>("Balance");
        balanceColumn.setCellValueFactory((TreeTableColumn.CellDataFeatures<Entry, Number> param) -> {
            return new ReadOnlyObjectWrapper<>(param.getValue().getValue().getValue());
        });
        balanceColumn.setCellFactory(p -> new CoinCell());
        table.getColumns().add(balanceColumn);
        table.setUnitFormat(masterWallets.get(0));

        CurrencyRate currencyRate = AppServices.getFiatCurrencyExchangeRate();
        if(currencyRate != null && currencyRate.isAvailable() && Config.get().getExchangeSource() != ExchangeSource.NONE) {
            TreeTableColumn<Entry, Number> fiatColumn = new TreeTableColumn<>(currencyRate.getCurrency().getSymbol());
            fiatColumn.setCellValueFactory((TreeTableColumn.CellDataFeatures<Entry, Number> param) -> {
                return new ReadOnlyObjectWrapper<>(param.getValue().getValue().getValue());
            });
            fiatColumn.setCellFactory(p -> new FiatCell());
            table.getColumns().add(fiatColumn);
            table.setCurrencyRate(currencyRate);
        }

        Entry rootEntry = allOpenWallets ? new AllSummaryEntry(walletFormsList) : new SummaryEntry(walletFormsList.get(0));
        TreeItem<Entry> rootItem = new TreeItem<>(rootEntry);
        for(Entry childEntry : rootEntry.getChildren()) {
            TreeItem<Entry> childItem = new TreeItem<>(childEntry);
            rootItem.getChildren().add(childItem);
            if(allOpenWallets) {
                for(Entry walletEntry : childEntry.getChildren()) {
                    TreeItem<Entry> walletItem = new TreeItem<>(walletEntry);
                    childItem.getChildren().add(walletItem);
                    walletItem.getChildren().add(new TreeItem<>(new UnconfirmedEntry((WalletTransactionsEntry)walletEntry)));
                }
            } else {
                childItem.getChildren().add(new TreeItem<>(new UnconfirmedEntry((WalletTransactionsEntry)childEntry)));
            }
        }

        table.setShowRoot(true);
        table.setRoot(rootItem);
        rootItem.setExpanded(true);

        table.setupColumnWidths();
        table.setPrefWidth(450);

        VBox vBox = new VBox();
        vBox.getChildren().add(table);

        hBox.getChildren().add(vBox);

        Wallet balanceWallet;
        if(allOpenWallets) {
            balanceWallet = new Wallet();
            balanceWallet.getChildWallets().addAll(masterWallets.stream().flatMap(mws -> mws.getAllWallets().stream()).toList());
        } else {
            balanceWallet = masterWallets.get(0);
        }

        NumberAxis xAxis = new NumberAxis();
        xAxis.setSide(Side.BOTTOM);
        xAxis.setForceZeroInRange(false);
        xAxis.setMinorTickVisible(false);
        NumberAxis yAxis = new NumberAxis();
        yAxis.setSide(Side.LEFT);
        BalanceChart balanceChart = new BalanceChart(xAxis, yAxis);
        balanceChart.initialize(new WalletTransactionsEntry(balanceWallet, true));
        balanceChart.setAnimated(false);
        balanceChart.setLegendVisible(false);
        balanceChart.setVerticalGridLinesVisible(false);

        hBox.getChildren().add(balanceChart);

        getDialogPane().setContent(hBox);

        ButtonType okButtonType = new javafx.scene.control.ButtonType("Done", ButtonBar.ButtonData.OK_DONE);
        dialogPane.getButtonTypes().addAll(okButtonType);

        setResizable(true);
        AppServices.moveToActiveWindowScreen(this);
    }

    public static class AllSummaryEntry extends Entry {
        private AllSummaryEntry(List<List<WalletForm>> walletFormsList) {
            super(null, "All Wallets", walletFormsList.stream().map(SummaryEntry::new).collect(Collectors.toList()));
        }

        @Override
        public Long getValue() {
            long value = 0;
            for(Entry entry : getChildren()) {
                value += entry.getValue();
            }

            return value;
        }

        @Override
        public String getEntryType() {
            return null;
        }

        @Override
        public Function getWalletFunction() {
            return Function.TRANSACTIONS;
        }
    }

    public static class SummaryEntry extends Entry {
        private SummaryEntry(List<WalletForm> walletForms) {
            super(walletForms.get(0).getWallet(), walletForms.get(0).getWallet().getName(), walletForms.stream().map(WalletForm::getWalletTransactionsEntry).collect(Collectors.toList()));
        }

        @Override
        public Long getValue() {
            long value = 0;
            for(Entry entry : getChildren()) {
                value += entry.getValue();
            }

            return value;
        }

        @Override
        public String getEntryType() {
            return null;
        }

        @Override
        public Function getWalletFunction() {
            return Function.TRANSACTIONS;
        }
    }

    public static class UnconfirmedEntry extends Entry {
        private final WalletTransactionsEntry walletTransactionsEntry;

        private UnconfirmedEntry(WalletTransactionsEntry walletTransactionsEntry) {
            super(walletTransactionsEntry.getWallet(), "Unconfirmed", Collections.emptyList());
            this.walletTransactionsEntry = walletTransactionsEntry;
        }

        @Override
        public Long getValue() {
            return walletTransactionsEntry.getMempoolBalance();
        }

        @Override
        public String getEntryType() {
            return null;
        }

        @Override
        public Function getWalletFunction() {
            return Function.TRANSACTIONS;
        }
    }
}
