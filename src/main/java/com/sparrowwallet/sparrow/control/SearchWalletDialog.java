package com.sparrowwallet.sparrow.control;

import com.csvreader.CsvWriter;
import com.sparrowwallet.drongo.BitcoinUnit;
import com.sparrowwallet.drongo.KeyPurpose;
import com.sparrowwallet.drongo.Utils;
import com.sparrowwallet.drongo.address.Address;
import com.sparrowwallet.drongo.address.InvalidAddressException;
import com.sparrowwallet.drongo.wallet.TableType;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.UnitFormat;
import com.sparrowwallet.sparrow.glyphfont.GlyphUtils;
import com.sparrowwallet.sparrow.io.Config;
import com.sparrowwallet.sparrow.wallet.*;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.ListChangeListener;
import javafx.event.ActionEvent;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.controlsfx.control.textfield.TextFields;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tornadofx.control.Field;
import tornadofx.control.Fieldset;
import tornadofx.control.Form;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

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
        dialogPane.setHeaderText(showWallet ? "Search All Wallets" : "Search Wallet " + walletForms.get(0).getMasterWallet().getName());

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
        results.setTableType(TableType.SEARCH_WALLET);
        results.setShowRoot(false);
        results.setPrefWidth(showWallet || showAccount ? 950 : 850);
        results.setUnitFormat(walletForms.iterator().next().getWallet());
        results.setPlaceholder(new Label("No results"));
        results.setEditable(true);

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
        labelCol.setCellFactory(p -> new LabelCell());
        results.getColumns().add(labelCol);

        TreeTableColumn<Entry, Number> amountCol = new TreeTableColumn<>("Value");
        amountCol.setCellValueFactory((TreeTableColumn.CellDataFeatures<Entry, Number> param) -> {
            return new ReadOnlyObjectWrapper<>(param.getValue().getValue().getValue());
        });
        amountCol.setCellFactory(p -> new CoinCell());
        results.getColumns().add(amountCol);

        vBox.getChildren().addAll(form, results);
        dialogPane.setContent(vBox);

        ButtonType exportButtonType = new ButtonType("Export CSV", ButtonBar.ButtonData.LEFT);
        ButtonType showButtonType = new javafx.scene.control.ButtonType("Show", ButtonBar.ButtonData.APPLY);
        ButtonType cancelButtonType = new javafx.scene.control.ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);

        dialogPane.getButtonTypes().addAll(exportButtonType, cancelButtonType, showButtonType);

        Button exportButton = (Button)dialogPane.lookupButton(exportButtonType);
        exportButton.setGraphic(GlyphUtils.getDownArrowGlyph());
        exportButton.addEventFilter(ActionEvent.ACTION, event -> {
            event.consume();
            exportResults(showWallet);
        });

        Button showButton = (Button)dialogPane.lookupButton(showButtonType);
        showButton.setDefaultButton(true);
        showButton.setDisable(true);

        setResultConverter(buttonType -> buttonType == showButtonType ? results.getSelectionModel().getSelectedItem().getValue() : null);

        results.getSelectionModel().getSelectedIndices().addListener((ListChangeListener<Integer>) c -> {
            showButton.setDisable(results.getSelectionModel().getSelectedCells().isEmpty()
                    || walletForms.stream().map(WalletForm::getWallet).noneMatch(wallet -> wallet == results.getSelectionModel().getSelectedItem().getValue().getWallet()));
        });

        search.textProperty().addListener((observable, oldValue, newValue) -> {
            searchWallets(newValue);
        });

        SearchWalletEntry rootEntry = new SearchWalletEntry(walletForms.getFirst().getWallet(), Collections.emptyList());
        RecursiveTreeItem<Entry> rootItem = new RecursiveTreeItem<>(rootEntry, Entry::getChildren);
        results.setRoot(rootItem);

        setResizable(true);
        results.setupColumnWidths();

        AppServices.moveToActiveWindowScreen(this);

        Platform.runLater(search::requestFocus);
    }

    public List<WalletForm> getWalletForms() {
        return walletForms;
    }

    private void searchWallets(String searchPhrase) {
        Set<Entry> matchingEntries = new LinkedHashSet<>();

        if(!searchPhrase.isEmpty()) {
            Set<String> searchWords = new LinkedHashSet<>(Arrays.stream(searchPhrase.split("\\s+"))
                    .filter(text -> isAddress(text) || isHash(text) || isHashIndex(text)).toList());
            String freeText = removeOccurrences(searchPhrase, searchWords).trim();
            if(!freeText.isEmpty()) {
                searchWords.add(freeText);
            }

            for(String searchText : searchWords) {
                Long searchValue = getSearchValue(searchText);
                Address searchAddress = getSearchAddress(searchText);
                searchText = searchText.toLowerCase(Locale.ROOT);

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
                                    hashIndexEntry.getHashIndex().toString().toLowerCase(Locale.ROOT).equals(searchText) ||
                                    (hashIndexEntry.getLabel() != null && hashIndexEntry.getLabel().toLowerCase(Locale.ROOT).contains(searchText)) ||
                                    (hashIndexEntry.getValue() != null && searchValue != null && Math.abs(hashIndexEntry.getValue()) == searchValue)) {
                                matchingEntries.add(entry);
                            }
                        }
                    }
                }
            }
        }

        SearchWalletEntry rootEntry = new SearchWalletEntry(walletForms.iterator().next().getWallet(), new ArrayList<>(matchingEntries));
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

    private boolean isAddress(String text) {
        try {
            Address.fromString(text);
            return true;
        } catch(InvalidAddressException e) {
            return false;
        }
    }

    private boolean isHash(String text) {
        return text.length() == 64 && Utils.isHex(text);
    }

    private boolean isHashIndex(String text) {
        String[] parts = text.split(":");
        if(parts.length == 2 && isHash(parts[0])) {
            try {
                Integer.parseInt(parts[1]);
                return true;
            } catch(NumberFormatException e) {
                //ignore
            }
        }

        return false;
    }

    private String removeOccurrences(String inputString, Collection<String> stringsToRemove) {
        for(String str : stringsToRemove) {
            inputString = inputString.replaceAll("(?i)" + str, "");
        }

        return inputString;
    }

    public void exportResults(boolean showWallet) {
        Stage window = new Stage();

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export search results to CSV");
        fileChooser.setInitialFileName(getDialogPane().getHeaderText() + ".csv");

        AppServices.moveToActiveWindowScreen(window, 800, 450);
        File file = fileChooser.showSaveDialog(window);
        if(file != null) {
            try(FileOutputStream outputStream = new FileOutputStream(file)) {
                CsvWriter writer = new CsvWriter(outputStream, ',', StandardCharsets.UTF_8);
                List<String> headers = new ArrayList<>(List.of("Wallet", "Account", "Type", "Date", "Txid / Address / Output", "Label", "Value"));
                if(!showWallet) {
                    headers.remove(0);
                }
                writer.writeRecord(headers.toArray(new String[0]));
                for(TreeItem<Entry> item : results.getRoot().getChildren()) {
                    Entry entry = item.getValue();
                    if(showWallet) {
                       writer.write(entry.getWallet().getMasterName());
                    }
                    writer.write(entry.getWallet().getDisplayName());
                    writer.write(entry.getEntryType());
                    if(entry instanceof TransactionEntry transactionEntry) {
                        writer.write(transactionEntry.getBlockTransaction().getDate() == null ? "Unconfirmed" : EntryCell.DATE_FORMAT.format(transactionEntry.getBlockTransaction().getDate()));
                        writer.write(transactionEntry.getBlockTransaction().getHash().toString());
                    } else if(entry instanceof NodeEntry nodeEntry) {
                        writer.write("");
                        writer.write(nodeEntry.getAddress().toString());
                    } else if(entry instanceof HashIndexEntry hashIndexEntry) {
                        writer.write(hashIndexEntry.getBlockTransaction().getDate() == null ? "Unconfirmed" : EntryCell.DATE_FORMAT.format(hashIndexEntry.getBlockTransaction().getDate()));
                        writer.write(hashIndexEntry.getHashIndex().toString());
                    } else {
                        writer.write("");
                        writer.write("");
                    }
                    writer.write(entry.getLabel());
                    writer.write(getCoinValue(entry.getValue() == null ? 0 : entry.getValue()));
                    writer.endRecord();
                }
                writer.close();
            } catch(IOException e) {
                log.error("Error exporting search results as CSV", e);
                AppServices.showErrorDialog("Error exporting search results as CSV", e.getMessage());
            }
        }
    }

    private String getCoinValue(Long value) {
        UnitFormat format = Config.get().getUnitFormat() == null ? UnitFormat.DOT : Config.get().getUnitFormat();
        return BitcoinUnit.BTC.equals(results.getBitcoinUnit()) ? format.tableFormatBtcValue(value) : String.format(Locale.ENGLISH, "%d", value);
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

            ContextMenu copyMenu;
            if(entry instanceof TransactionEntry transactionEntry) {
                copyMenu = new TransactionContextMenu(getText(), transactionEntry.getBlockTransaction());
            } else if(entry instanceof NodeEntry nodeEntry) {
                copyMenu = new AddressContextMenu(nodeEntry.getAddress(), nodeEntry.getOutputDescriptor(), null, false, null);
            } else if(entry instanceof UtxoEntry utxoEntry) {
                copyMenu = new HashIndexEntryContextMenu(null, utxoEntry);
            } else {
                copyMenu = new ContextMenu();
            }
            copyMenu.getItems().removeIf(menuItem -> !menuItem.getText().startsWith("Copy"));
            setContextMenu(copyMenu);
        }
    }
}
