package com.sparrowwallet.sparrow.control;

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

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class WalletSummaryDialog extends Dialog<Void> {
    public WalletSummaryDialog(List<WalletForm> walletForms) {
        if(walletForms.isEmpty()) {
            throw new IllegalArgumentException("No wallets selected to summarize");
        }

        Wallet masterWallet = walletForms.get(0).getMasterWallet();

        final DialogPane dialogPane = getDialogPane();
        dialogPane.getStylesheets().add(AppServices.class.getResource("general.css").toExternalForm());
        dialogPane.getStylesheets().add(AppServices.class.getResource("dialog.css").toExternalForm());
        dialogPane.getStylesheets().add(AppServices.class.getResource("wallet/wallet.css").toExternalForm());
        dialogPane.getStylesheets().add(AppServices.class.getResource("wallet/transactions.css").toExternalForm());

        AppServices.setStageIcon(dialogPane.getScene().getWindow());
        dialogPane.setHeaderText("Wallet Summary for " + masterWallet.getName());

        Image image = new Image("image/sparrow-small.png", 50, 50, false, false);
        if(!image.isError()) {
            ImageView imageView = new ImageView();
            imageView.setSmooth(false);
            imageView.setImage(image);
            dialogPane.setGraphic(imageView);
        }

        HBox hBox = new HBox(40);

        CoinTreeTable table = new CoinTreeTable();

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
        table.setUnitFormat(masterWallet);

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

        SummaryEntry rootEntry = new SummaryEntry(walletForms);
        TreeItem<Entry> rootItem = new TreeItem<>(rootEntry);
        for(Entry childEntry : rootEntry.getChildren()) {
            TreeItem<Entry> childItem = new TreeItem<>(childEntry);
            rootItem.getChildren().add(childItem);
            childItem.getChildren().add(new TreeItem<>(new UnconfirmedEntry((WalletTransactionsEntry)childEntry)));
        }

        table.setShowRoot(true);
        table.setRoot(rootItem);
        rootItem.setExpanded(true);

        table.setColumnResizePolicy(TreeTableView.CONSTRAINED_RESIZE_POLICY);
        table.setPrefWidth(450);

        VBox vBox = new VBox();
        vBox.getChildren().add(table);

        hBox.getChildren().add(vBox);

        NumberAxis xAxis = new NumberAxis();
        xAxis.setSide(Side.BOTTOM);
        xAxis.setForceZeroInRange(false);
        xAxis.setMinorTickVisible(false);
        NumberAxis yAxis = new NumberAxis();
        yAxis.setSide(Side.LEFT);
        BalanceChart balanceChart = new BalanceChart(xAxis, yAxis);
        balanceChart.initialize(new WalletTransactionsEntry(masterWallet, true));
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
