package com.sparrowwallet.sparrow.control;

import com.csvreader.CsvReader;
import com.sparrowwallet.drongo.BitcoinUnit;
import com.sparrowwallet.drongo.address.Address;
import com.sparrowwallet.drongo.address.InvalidAddressException;
import com.sparrowwallet.drongo.protocol.Transaction;
import com.sparrowwallet.drongo.wallet.Payment;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.glyphfont.FontAwesome5;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Clipboard;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;
import javafx.util.StringConverter;
import org.controlsfx.control.spreadsheet.*;
import org.controlsfx.glyphfont.Glyph;
import org.controlsfx.tools.Platform;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class SendToManyDialog extends Dialog<List<Payment>> {
    private final BitcoinUnit bitcoinUnit;
    private final SpreadsheetView spreadsheetView;
    public static final AddressCellType ADDRESS = new AddressCellType();

    public SendToManyDialog(BitcoinUnit bitcoinUnit) {
        this.bitcoinUnit = bitcoinUnit;

        final DialogPane dialogPane = new SendToManyDialogPane();
        setDialogPane(dialogPane);
        setTitle("Send to Many");
        dialogPane.getStylesheets().add(AppServices.class.getResource("general.css").toExternalForm());
        dialogPane.setHeaderText("Send to many recipients by specifying addresses and amounts.\nOnly the first row's label is necessary.");
        Image image = new Image("/image/sparrow-small.png");
        dialogPane.setGraphic(new ImageView(image));

        List<Payment> initialPayments = IntStream.range(0, 100).mapToObj(i -> new Payment(null, null, -1, false)).collect(Collectors.toList());
        Grid grid = getGrid(initialPayments);

        spreadsheetView = new SpreadsheetView(grid) {
            @Override
            public void pasteClipboard() {
                final Clipboard clipboard = Clipboard.getSystemClipboard();
                if(clipboard.hasString()) {
                    final TablePosition<?,?> tp = getSelectionModel().getFocusedCell();
                    SpreadsheetCell cell = getGrid().getRows().get(tp.getRow()).get(tp.getColumn());
                    getGrid().setCellValue(cell.getRow(), cell.getColumn(), cell.getCellType().convertValue(clipboard.getString()));
                } else {
                    super.pasteClipboard();
                }
            }
        };
        spreadsheetView.getColumns().get(0).setPrefWidth(400);
        spreadsheetView.getColumns().get(1).setPrefWidth(150);
        spreadsheetView.getColumns().get(2).setPrefWidth(247);

        StackPane stackPane = new StackPane();
        stackPane.getChildren().add(spreadsheetView);
        dialogPane.setContent(stackPane);

        dialogPane.getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        final ButtonType loadCsvButtonType = new javafx.scene.control.ButtonType("Load CSV", ButtonBar.ButtonData.LEFT);
        dialogPane.getButtonTypes().add(loadCsvButtonType);

        setResultConverter((dialogButton) -> {
            ButtonBar.ButtonData data = dialogButton == null ? null : dialogButton.getButtonData();
            return data == ButtonBar.ButtonData.OK_DONE ? getPayments() : null;
        });

        dialogPane.setPrefWidth(850);
        dialogPane.setPrefHeight(500);
        setResizable(true);
        AppServices.setStageIcon(dialogPane.getScene().getWindow());
        AppServices.moveToActiveWindowScreen(this);
    }

    private Grid getGrid(List<Payment> payments) {
        int rowCount = payments.size();
        int columnCount = 3;
        GridBase grid = new GridBase(rowCount, columnCount);
        ObservableList<ObservableList<SpreadsheetCell>> rows = FXCollections.observableArrayList();
        for(int row = 0; row < grid.getRowCount(); ++row) {
            final ObservableList<SpreadsheetCell> list = FXCollections.observableArrayList();

            SpreadsheetCell addressCell = ADDRESS.createCell(row, 0, 1, 1, payments.get(row).getAddress());
            addressCell.getStyleClass().add("fixed-width");
            list.add(addressCell);

            double amount = (double)payments.get(row).getAmount();
            if(bitcoinUnit == BitcoinUnit.BTC) {
                amount = amount / Transaction.SATOSHIS_PER_BITCOIN;
            }
            SpreadsheetCell amountCell = SpreadsheetCellType.DOUBLE.createCell(row, 1, 1, 1, amount < 0 ? null : amount);
            amountCell.setFormat(bitcoinUnit == BitcoinUnit.BTC ? "0.00000000" : "###,###");
            amountCell.getStyleClass().add("number-value");
            if(Platform.getCurrent() == Platform.OSX) {
                amountCell.getStyleClass().add("number-field");
            }
            list.add(amountCell);

            list.add(SpreadsheetCellType.STRING.createCell(row, 2, 1, 1, payments.get(row).getLabel()));
            rows.add(list);
        }
        grid.setRows(rows);
        grid.getColumnHeaders().setAll("Address", "Amount (" + bitcoinUnit.getLabel() + ")", "Label");

        return grid;
    }

    private List<Payment> getPayments() {
        List<Payment> payments = new ArrayList<>();
        Grid grid = spreadsheetView.getGrid();
        String firstLabel = null;
        for(int row = 0; row < grid.getRowCount(); row++) {
            ObservableList<SpreadsheetCell> rowCells = spreadsheetView.getItems().get(row);
            Address address = (Address)rowCells.get(0).getItem();
            Double value = (Double)rowCells.get(1).getItem();
            String label = (String)rowCells.get(2).getItem();
            if(firstLabel == null) {
                firstLabel = label;
            }
            if(label == null || label.isEmpty()) {
                label = firstLabel;
            }

            if(address != null && value != null) {
                if(bitcoinUnit == BitcoinUnit.BTC) {
                    value = value * Transaction.SATOSHIS_PER_BITCOIN;
                }

                payments.add(new Payment(address, label, value.longValue(), false));
            }
        }

        return payments;
    }

    private class SendToManyDialogPane extends DialogPane {
        @Override
        protected Node createButton(ButtonType buttonType) {
            Node button;
            if(buttonType.getButtonData() == ButtonBar.ButtonData.LEFT) {
                Button loadButton = new Button(buttonType.getText());
                loadButton.setGraphicTextGap(5);
                loadButton.setGraphic(getGlyph(FontAwesome5.Glyph.ARROW_UP));
                final ButtonBar.ButtonData buttonData = buttonType.getButtonData();
                ButtonBar.setButtonData(loadButton, buttonData);
                loadButton.setOnAction(event -> {
                    FileChooser fileChooser = new FileChooser();
                    fileChooser.setTitle("Open CSV");
                    fileChooser.getExtensionFilters().addAll(
                            new FileChooser.ExtensionFilter("All Files", org.controlsfx.tools.Platform.getCurrent().equals(org.controlsfx.tools.Platform.UNIX) ? "*" : "*.*"),
                            new FileChooser.ExtensionFilter("CSV", "*.csv")
                    );

                    AppServices.moveToActiveWindowScreen(this.getScene().getWindow(), 800, 450);
                    File file = fileChooser.showOpenDialog(this.getScene().getWindow());
                    if(file != null) {
                        try {
                            List<Payment> csvPayments = new ArrayList<>();
                            try(Reader reader = new FileReader(file, StandardCharsets.UTF_8)) {
                                CsvReader csvReader = new CsvReader(reader);
                                while(csvReader.readRecord()) {
                                    if(csvReader.getColumnCount() < 2) {
                                        continue;
                                    }

                                    try {
                                        long amount;
                                        if(bitcoinUnit == BitcoinUnit.BTC) {
                                            double doubleAmount = Double.parseDouble(csvReader.get(1).replace(",", ""));
                                            amount = (long)(doubleAmount * Transaction.SATOSHIS_PER_BITCOIN);
                                        } else {
                                            amount = Long.parseLong(csvReader.get(1).replace(",", ""));
                                        }
                                        Address address = Address.fromString(csvReader.get(0));
                                        String label = csvReader.get(2);
                                        csvPayments.add(new Payment(address, label, amount, false));
                                    } catch(NumberFormatException e) {
                                        //ignore and continue - probably a header line
                                    } catch(InvalidAddressException e) {
                                        AppServices.showErrorDialog("Invalid Address", e.getMessage());
                                    }
                                }

                                if(csvPayments.isEmpty()) {
                                    AppServices.showErrorDialog("No recipients found", "No valid recipients were found. Use a CSV file with three columns, and ensure amounts are in " + bitcoinUnit.getLabel() + ".");
                                    return;
                                }

                                spreadsheetView.setGrid(getGrid(csvPayments));
                            }
                        } catch(IOException e) {
                            AppServices.showErrorDialog("Cannot load CSV", e.getMessage());
                        }
                    }
                });

                button = loadButton;
            } else {
                button = super.createButton(buttonType);
            }

            return button;
        }

        private Glyph getGlyph(FontAwesome5.Glyph glyphName) {
            Glyph glyph = new Glyph(FontAwesome5.FONT_NAME, glyphName);
            glyph.setFontSize(11);
            return glyph;
        }
    }

    public static class AddressCellType extends SpreadsheetCellType<Address> {
        public AddressCellType() {
            this(new StringConverterWithFormat<>(new AddressStringConverter()) {
                @Override
                public String toString(Address item) {
                    return toStringFormat(item, ""); //$NON-NLS-1$
                }

                @Override
                public Address fromString(String str) {
                    if(str == null || str.isEmpty()) { //$NON-NLS-1$
                        return null;
                    } else {
                        return myConverter.fromString(str);
                    }
                }

                @Override
                public String toStringFormat(Address item, String format) {
                    try {
                        if(item == null) {
                            return ""; //$NON-NLS-1$
                        } else {
                            return item.toString();
                        }
                    } catch (Exception ex) {
                        return myConverter.toString(item);
                    }
                }
            });
        }

        public AddressCellType(StringConverter<Address> converter) {
            super(converter);
        }

        @Override
        public String toString() {
            return "address";
        }

        public SpreadsheetCell createCell(final int row, final int column, final int rowSpan, final int columnSpan,
                                          final Address value) {
            SpreadsheetCell cell = new SpreadsheetCellBase(row, column, rowSpan, columnSpan, this);
            cell.setItem(value);
            return cell;
        }

        @Override
        public SpreadsheetCellEditor createEditor(SpreadsheetView view) {
            return new SpreadsheetCellEditor.StringEditor(view);
        }

        @Override
        public boolean match(Object value, Object... options) {
            if(value instanceof Address)
                return true;
            else {
                try {
                    converter.fromString(value == null ? null : value.toString());
                    return true;
                } catch (Exception e) {
                    return false;
                }
            }
        }

        @Override
        public Address convertValue(Object value) {
            if(value instanceof Address)
                return (Address)value;
            else {
                try {
                    return converter.fromString(value == null ? null : value.toString());
                } catch (Exception e) {
                    return null;
                }
            }
        }

        @Override
        public String toString(Address item) {
            return converter.toString(item);
        }

        @Override
        public String toString(Address item, String format) {
            return ((StringConverterWithFormat<Address>)converter).toStringFormat(item, format);
        }
    };
}
