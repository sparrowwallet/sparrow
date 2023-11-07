package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.drongo.KeyPurpose;
import com.sparrowwallet.drongo.address.Address;
import com.sparrowwallet.drongo.address.InvalidAddressException;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.wallet.*;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.ListChangeListener;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import org.controlsfx.control.textfield.TextFields;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tornadofx.control.Field;
import tornadofx.control.Fieldset;
import tornadofx.control.Form;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class SearchWalletDialog extends Dialog<Entry> {
    private static final Logger log = LoggerFactory.getLogger(SearchWalletDialog.class);

    private final List<WalletForm> walletForms;
    private final TextField search;
    private final CoinTreeTable results;

    public SearchWalletDialog(List<WalletForm> walletForms) {
        this.walletForms = walletForms;

        if(walletForms.isEmpty()) {
            throw new IllegalArgumentException("No wallets selected to search");
        }

        boolean showWallet = walletForms.stream().map(WalletForm::getMasterWallet).distinct().limit(2).count() > 1;
        boolean showAccount = walletForms.stream().anyMatch(walletForm -> !walletForm.getWallet().isMasterWallet() || !walletForm.getNestedWalletForms().isEmpty());

        final DialogPane dialogPane = getDialogPane();
        dialogPane.getStylesheets().add(AppServices.class.getResource("general.css").toExternalForm());
        dialogPane.getStylesheets().add(AppServices.class.getResource("dialog.css").toExternalForm());
        dialogPane.getStylesheets().add(AppServices.class.getResource("wallet/wallet.css").toExternalForm());
        dialogPane.getStylesheets().add(AppServices.class.getResource("search.css").toExternalForm());
        AppServices.setStageIcon(dialogPane.getScene().getWindow());
        dialogPane.setHeaderText(showWallet ? "Search All Wallets" : "Search Wallet");

        Image image = new Image("image/sparrow-small.png", 50, 50, false, false);
        if(!image.isError()) {
            ImageView imageView = new ImageView();
            imageView.setSmooth(false);
            imageView.setImage(image);
            dialogPane.setGraphic(imageView);
        }

        VBox vBox = new VBox();
        vBox.setSpacing(20);

        Form form = new Form();
        Fieldset fieldset = new Fieldset();
        fieldset.setText("");
        fieldset.setSpacing(10);

        Field searchField = new Field();
        searchField.setText("Search:");
        search = TextFields.createClearableTextField();
        search.setPromptText("Label, address, value or transaction ID");
        searchField.getInputs().add(search);

        fieldset.getChildren().addAll(searchField);
        form.getChildren().add(fieldset);

        results = new CoinTreeTable();
        results.setShowRoot(false);
        results.setPrefWidth(showWallet || showAccount ? 950 : 850);
        results.setUnitFormat(walletForms.iterator().next().getWallet());
        results.setColumnResizePolicy(TreeTableView.CONSTRAINED_RESIZE_POLICY);
        results.setPlaceholder(new Label("No results"));

        if(showWallet) {
            TreeTableColumn<Entry, String> walletColumn = new TreeTableColumn<>("Wallet");
            walletColumn.setCellValueFactory((TreeTableColumn.CellDataFeatures<Entry, String> param) -> {
                return new ReadOnlyObjectWrapper<>(param.getValue().getValue().getWallet().getMasterName());
            });
            results.getColumns().add(walletColumn);
        }

        if(showAccount) {
            TreeTableColumn<Entry, String> accountColumn = new TreeTableColumn<>("Account");
            accountColumn.setCellValueFactory((TreeTableColumn.CellDataFeatures<Entry, String> param) -> {
                return new ReadOnlyObjectWrapper<>(param.getValue().getValue().getWallet().getDisplayName());
            });
            results.getColumns().add(accountColumn);
        }

        TreeTableColumn<Entry, String> typeColumn = new TreeTableColumn<>("Type");
        typeColumn.setCellValueFactory((TreeTableColumn.CellDataFeatures<Entry, String> param) -> {
            return new ReadOnlyObjectWrapper<>(param.getValue().getValue().getEntryType());
        });
        results.getColumns().add(typeColumn);

        TreeTableColumn<Entry, Entry> entryCol = new TreeTableColumn<>("Date / Address / Output");
        entryCol.setCellValueFactory((TreeTableColumn.CellDataFeatures<Entry, Entry> param) -> {
            return new ReadOnlyObjectWrapper<>(param.getValue().getValue());
        });
        entryCol.setCellFactory(p -> new SearchEntryCell());
        String address = walletForms.iterator().next().getNodeEntry(KeyPurpose.RECEIVE).getAddress().toString();
        entryCol.setMinWidth(TextUtils.computeTextWidth(AppServices.getMonospaceFont(), address, 0.0));
        results.getColumns().add(entryCol);

        TreeTableColumn<Entry, String> labelCol = new TreeTableColumn<>("Label");
        labelCol.setCellValueFactory((TreeTableColumn.CellDataFeatures<Entry, String> param) -> {
            return param.getValue().getValue().labelProperty();
        });
        labelCol.setCellFactory(p -> new SearchLabelCell());
        results.getColumns().add(labelCol);

        TreeTableColumn<Entry, Number> amountCol = new TreeTableColumn<>("Value");
        amountCol.setCellValueFactory((TreeTableColumn.CellDataFeatures<Entry, Number> param) -> {
            return new ReadOnlyObjectWrapper<>(param.getValue().getValue().getValue());
        });
        amountCol.setCellFactory(p -> new CoinCell());
        results.getColumns().add(amountCol);

        vBox.getChildren().addAll(form, results);
        dialogPane.setContent(vBox);

        ButtonType showButtonType = new javafx.scene.control.ButtonType("Show", ButtonBar.ButtonData.APPLY);
        ButtonType cancelButtonType = new javafx.scene.control.ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);

        dialogPane.getButtonTypes().addAll(cancelButtonType, showButtonType);

        Button showButton = (Button) dialogPane.lookupButton(showButtonType);
        showButton.setDefaultButton(true);
        showButton.setDisable(true);

        setResultConverter(buttonType -> buttonType == showButtonType ? results.getSelectionModel().getSelectedItem().getValue() : null);

        results.getSelectionModel().getSelectedIndices().addListener((ListChangeListener<Integer>) c -> {
            showButton.setDisable(results.getSelectionModel().getSelectedCells().isEmpty()
                    || walletForms.stream().map(WalletForm::getWallet).noneMatch(wallet -> wallet == results.getSelectionModel().getSelectedItem().getValue().getWallet()));
        });

        search.textProperty().addListener((observable, oldValue, newValue) -> {
            searchWallets(newValue.toLowerCase(Locale.ROOT));
        });

        setResizable(true);

        AppServices.moveToActiveWindowScreen(this);

        Platform.runLater(search::requestFocus);
    }

    private void searchWallets(String searchText) {
        List<Entry> matchingEntries = new ArrayList<>();

        if(!searchText.isEmpty()) {
            Long searchValue = getSearchValue(searchText);
            Address searchAddress = getSearchAddress(searchText);

            for(WalletForm walletForm : walletForms) {
                WalletTransactionsEntry walletTransactionsEntry = walletForm.getWalletTransactionsEntry();
                for(Entry entry : walletTransactionsEntry.getChildren()) {
                    if(entry instanceof TransactionEntry transactionEntry) {
                        if(transactionEntry.getBlockTransaction().getHash().toString().equals(searchText) ||
                                (transactionEntry.getLabel() != null && transactionEntry.getLabel().toLowerCase(Locale.ROOT).contains(searchText)) ||
                                (transactionEntry.getValue() != null && searchValue != null && Math.abs(transactionEntry.getValue()) == searchValue) ||
                                (searchAddress != null && transactionEntry.getBlockTransaction().getTransaction().getOutputs().stream().map(output -> output.getScript().getToAddress()).filter(Objects::nonNull).anyMatch(address -> address.equals(searchAddress)))) {
                            matchingEntries.add(entry);
                        }
                    }
                }

                for(KeyPurpose keyPurpose : KeyPurpose.DEFAULT_PURPOSES) {
                    NodeEntry purposeEntry = walletForm.getNodeEntry(keyPurpose);
                    for(Entry entry : purposeEntry.getChildren()) {
                        if(entry instanceof NodeEntry nodeEntry) {
                            if(nodeEntry.getAddress().toString().toLowerCase(Locale.ROOT).contains(searchText) ||
                                    (nodeEntry.getLabel() != null && nodeEntry.getLabel().toLowerCase(Locale.ROOT).contains(searchText)) ||
                                    (nodeEntry.getValue() != null && searchValue != null && Math.abs(nodeEntry.getValue()) == searchValue)) {
                                matchingEntries.add(entry);
                            }
                        }
                    }
                }

                for(WalletForm nestedWalletForm : walletForm.getNestedWalletForms()) {
                    for(KeyPurpose keyPurpose : nestedWalletForm.getWallet().getWalletKeyPurposes()) {
                        NodeEntry purposeEntry = nestedWalletForm.getNodeEntry(keyPurpose);
                        for(Entry entry : purposeEntry.getChildren()) {
                            if(entry instanceof NodeEntry nodeEntry) {
                                if(nodeEntry.getAddress().toString().toLowerCase(Locale.ROOT).contains(searchText) ||
                                        (nodeEntry.getLabel() != null && nodeEntry.getLabel().toLowerCase(Locale.ROOT).contains(searchText)) ||
                                        (nodeEntry.getValue() != null && searchValue != null && Math.abs(nodeEntry.getValue()) == searchValue)) {
                                    matchingEntries.add(entry);
                                }
                            }
                        }
                    }
                }

                WalletUtxosEntry walletUtxosEntry = walletForm.getWalletUtxosEntry();
                for(Entry entry : walletUtxosEntry.getChildren()) {
                    if(entry instanceof HashIndexEntry hashIndexEntry) {
                        if(hashIndexEntry.getBlockTransaction().getHash().toString().toLowerCase(Locale.ROOT).equals(searchText) ||
                                (hashIndexEntry.getLabel() != null && hashIndexEntry.getLabel().toLowerCase(Locale.ROOT).contains(searchText)) ||
                                (hashIndexEntry.getValue() != null && searchValue != null && Math.abs(hashIndexEntry.getValue()) == searchValue)) {
                            matchingEntries.add(entry);
                        }
                    }
                }
            }
        }

        SearchWalletEntry rootEntry = new SearchWalletEntry(walletForms.iterator().next().getWallet(), matchingEntries);
        RecursiveTreeItem<Entry> rootItem = new RecursiveTreeItem<>(rootEntry, Entry::getChildren);
        results.setRoot(rootItem);
    }

    private Long getSearchValue(String searchText) {
        try {
            return Math.abs(Long.parseLong(searchText));
        } catch(NumberFormatException e) {
            return null;
        }
    }

    private Address getSearchAddress(String searchText) {
        try {
            return Address.fromString(searchText);
        } catch(InvalidAddressException e) {
            return null;
        }
    }

    private static class SearchWalletEntry extends Entry {
        public SearchWalletEntry(Wallet wallet, List<Entry> entries) {
            super(wallet, wallet.getName(), entries);
        }

        @Override
        public Long getValue() {
            return 0L;
        }

        @Override
        public String getEntryType() {
            return "Search Wallet Results";
        }

        @Override
        public Function getWalletFunction() {
            return null;
        }
    }

    private static class SearchEntryCell extends EntryCell {
        @Override
        protected void updateItem(Entry entry, boolean empty) {
            super.updateItem(entry, empty);
            setContextMenu(null);
        }
    }

    private static class SearchLabelCell extends LabelCell {
        @Override
        public void updateItem(String label, boolean empty) {
            super.updateItem(label, empty);
            setContextMenu(null);
        }
    }
}
