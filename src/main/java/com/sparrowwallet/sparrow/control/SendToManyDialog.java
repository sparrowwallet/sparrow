package com.sparrowwallet.sparrow.control;

import com.csvreader.CsvReader;
import com.sparrowwallet.drongo.BitcoinUnit;
import com.sparrowwallet.drongo.OsType;
import com.sparrowwallet.drongo.address.Address;
import com.sparrowwallet.drongo.address.InvalidAddressException;
import com.sparrowwallet.drongo.dns.DnsPayment;
import com.sparrowwallet.drongo.dns.DnsPaymentCache;
import com.sparrowwallet.drongo.dns.DnsPaymentResolver;
import com.sparrowwallet.drongo.dns.DnsPaymentValidationException;
import com.sparrowwallet.drongo.protocol.Transaction;
import com.sparrowwallet.drongo.silentpayments.SilentPayment;
import com.sparrowwallet.drongo.silentpayments.SilentPaymentAddress;
import com.sparrowwallet.drongo.uri.BitcoinURIParseException;
import com.sparrowwallet.drongo.wallet.Payment;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.event.RequestConnectEvent;
import com.sparrowwallet.sparrow.glyphfont.GlyphUtils;
import com.sparrowwallet.sparrow.io.Config;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;
import javafx.util.StringConverter;
import org.controlsfx.control.spreadsheet.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class SendToManyDialog extends Dialog<List<Payment>> {
    private final BitcoinUnit bitcoinUnit;
    private final SpreadsheetView spreadsheetView;
    public static final SendToAddressCellType SEND_TO_ADDRESS = new SendToAddressCellType();

    public SendToManyDialog(BitcoinUnit bitcoinUnit, List<Payment> payments) {
        this.bitcoinUnit = bitcoinUnit;

        final DialogPane dialogPane = new SendToManyDialogPane();
        setDialogPane(dialogPane);
        setTitle("Send to Many");
        dialogPane.getStylesheets().add(AppServices.class.getResource("general.css").toExternalForm());
        dialogPane.setHeaderText("Send to many recipients by specifying addresses and amounts.\nOnly the first row's label is necessary.");
        dialogPane.setGraphic(new DialogImage(DialogImage.Type.SPARROW));

        List<Payment> initialPayments = IntStream.range(0, 100)
                .mapToObj(i -> i < payments.size() ? payments.get(i) : new Payment(null, null, -1, false)).collect(Collectors.toList());
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
        Button okButton = (Button) dialogPane.lookupButton(ButtonType.OK);
        okButton.addEventFilter(ActionEvent.ACTION, event -> {
            getPayments();
            event.consume();
        });

        final ButtonType loadCsvButtonType = new javafx.scene.control.ButtonType("Load CSV", ButtonBar.ButtonData.LEFT);
        dialogPane.getButtonTypes().add(loadCsvButtonType);

        setResultConverter((_) -> null);

        dialogPane.setPrefWidth(850);
        dialogPane.setPrefHeight(500);
        setResizable(true);
        AppServices.setStageIcon(dialogPane.getScene().getWindow());
        AppServices.moveToActiveWindowScreen(this);
    }

    private Grid getGrid(List<Payment> payments) {
        return createGrid(payments.stream().map(payment -> new SendToPayment(payment, SendToAddress.fromPayment(payment))).collect(Collectors.toList()));
    }

    private Grid createGrid(List<SendToPayment> sendToPayments) {
        int rowCount = sendToPayments.size();
        int columnCount = 3;
        GridBase grid = new GridBase(rowCount, columnCount);
        ObservableList<ObservableList<SpreadsheetCell>> rows = FXCollections.observableArrayList();
        for(int row = 0; row < grid.getRowCount(); ++row) {
            SendToPayment sendToPayment = sendToPayments.get(row);
            final ObservableList<SpreadsheetCell> list = FXCollections.observableArrayList();

            SendToAddress sendToAddress = sendToPayment.sendToAddress();
            SpreadsheetCell addressCell = SEND_TO_ADDRESS.createCell(row, 0, 1, 1, sendToAddress);
            addressCell.getStyleClass().add("fixed-width");
            list.add(addressCell);

            double amount = (double)sendToPayment.payment().getAmount();
            if(bitcoinUnit == BitcoinUnit.BTC) {
                amount = amount / Transaction.SATOSHIS_PER_BITCOIN;
            }
            SpreadsheetCell amountCell = SpreadsheetCellType.DOUBLE.createCell(row, 1, 1, 1, amount < 0 ? null : amount);
            amountCell.setFormat(bitcoinUnit == BitcoinUnit.BTC ? "0.00000000" : "###,###");
            amountCell.getStyleClass().add("number-value");
            if(OsType.getCurrent() == OsType.MACOS) {
                amountCell.getStyleClass().add("number-field");
            }
            list.add(amountCell);

            list.add(SpreadsheetCellType.STRING.createCell(row, 2, 1, 1, sendToPayment.payment().getLabel()));
            rows.add(list);
        }
        grid.setRows(rows);
        grid.getColumnHeaders().setAll("Address", "Amount (" + bitcoinUnit.getLabel() + ")", "Label");

        return grid;
    }

    private void getPayments() {
        if(needsResolution() && Config.get().hasServer() && !AppServices.isConnected() && !AppServices.isConnecting()) {
            if(Config.get().getConnectToResolve() == null || Config.get().getConnectToResolve() == Boolean.FALSE) {
                Platform.runLater(() -> {
                    ConfirmationAlert confirmationAlert = new ConfirmationAlert("Connect to resolve?", "You are currently offline. Connect to resolve the addresses?", ButtonType.NO, ButtonType.YES);
                    Optional<ButtonType> optType = confirmationAlert.showAndWait();
                    if(confirmationAlert.isDontAskAgain() && optType.isPresent()) {
                        Config.get().setConnectToResolve(optType.get() == ButtonType.YES);
                    }
                    if(optType.isPresent() && optType.get() == ButtonType.YES) {
                        EventManager.get().post(new RequestConnectEvent());
                    }
                });
            } else {
                Platform.runLater(() -> EventManager.get().post(new RequestConnectEvent()));
            }
            return;
        }

        CreatePaymentsService createPaymentsService = new CreatePaymentsService();
        createPaymentsService.setOnSucceeded(_ -> {
            List<Payment> payments = createPaymentsService.getValue();
            if(payments != null) {
                setResult(payments);
            }
        });
        createPaymentsService.setOnFailed(event -> {
            Throwable ex = event.getSource().getException();
            AppServices.showErrorDialog("Error creating payments", ex.getMessage());
        });
        createPaymentsService.start();
    }

    private boolean needsResolution() {
        for(int row = 0; row < spreadsheetView.getGrid().getRowCount(); row++) {
            ObservableList<SpreadsheetCell> rowCells = spreadsheetView.getItems().get(row);
            SendToAddress sendToAddress = (SendToAddress)rowCells.getFirst().getItem();
            if(sendToAddress.hrn != null && DnsPaymentCache.getDnsPayment(sendToAddress.hrn) == null) {
                return true;
            }
        }

        return false;
    }

    private class SendToManyDialogPane extends DialogPane {
        @Override
        protected Node createButton(ButtonType buttonType) {
            Node button;
            if(buttonType.getButtonData() == ButtonBar.ButtonData.LEFT) {
                Button loadButton = new Button(buttonType.getText());
                loadButton.setGraphicTextGap(5);
                loadButton.setGraphic(GlyphUtils.getUpArrowGlyph());
                final ButtonBar.ButtonData buttonData = buttonType.getButtonData();
                ButtonBar.setButtonData(loadButton, buttonData);
                loadButton.setOnAction(event -> {
                    FileChooser fileChooser = new FileChooser();
                    fileChooser.setTitle("Open CSV");
                    fileChooser.getExtensionFilters().addAll(
                            new FileChooser.ExtensionFilter("All Files", OsType.getCurrent().equals(OsType.UNIX) ? "*" : "*.*"),
                            new FileChooser.ExtensionFilter("CSV", "*.csv")
                    );

                    AppServices.moveToActiveWindowScreen(this.getScene().getWindow(), 800, 450);
                    File file = fileChooser.showOpenDialog(this.getScene().getWindow());
                    if(file != null) {
                        try {
                            List<SendToPayment> csvPayments = new ArrayList<>();
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
                                        String label = csvReader.get(2);
                                        Optional<String> optDnsPaymentHrn = DnsPayment.getHrn(csvReader.get(0));
                                        if(optDnsPaymentHrn.isPresent()) {
                                            Payment payment = new Payment(null, label, amount, false);
                                            csvPayments.add(new SendToPayment(payment, new SendToAddress(optDnsPaymentHrn.get())));
                                        } else {
                                            try {
                                                SilentPaymentAddress silentPaymentAddress = SilentPaymentAddress.from(csvReader.get(0));
                                                Payment payment = new SilentPayment(silentPaymentAddress, label, amount, false);
                                                csvPayments.add(new SendToPayment(payment, SendToAddress.fromPayment(payment)));
                                            } catch(Exception e) {
                                                Address address = Address.fromString(csvReader.get(0));
                                                Payment payment = new Payment(address, label, amount, false);
                                                csvPayments.add(new SendToPayment(payment, SendToAddress.fromPayment(payment)));
                                            }
                                        }
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

                                spreadsheetView.setGrid(createGrid(csvPayments));
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
    }

    public static class SendToAddressCellType extends SpreadsheetCellType<SendToAddress> {
        public SendToAddressCellType() {
            this(new StringConverterWithFormat<>(new SendToAddressStringConverter()) {
                @Override
                public String toString(SendToAddress item) {
                    return toStringFormat(item, ""); //$NON-NLS-1$
                }

                @Override
                public SendToAddress fromString(String str) {
                    if(str == null || str.isEmpty()) { //$NON-NLS-1$
                        return null;
                    } else {
                        return myConverter.fromString(str);
                    }
                }

                @Override
                public String toStringFormat(SendToAddress item, String format) {
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

        public SendToAddressCellType(StringConverter<SendToAddress> converter) {
            super(converter);
        }

        @Override
        public String toString() {
            return "address";
        }

        public SpreadsheetCell createCell(final int row, final int column, final int rowSpan, final int columnSpan,
                                          final SendToAddress value) {
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
            if(value instanceof SendToAddress)
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
        public SendToAddress convertValue(Object value) {
            if(value instanceof SendToAddress)
                return (SendToAddress)value;
            else {
                try {
                    return converter.fromString(value == null ? null : value.toString());
                } catch (Exception e) {
                    return null;
                }
            }
        }

        @Override
        public String toString(SendToAddress item) {
            return converter.toString(item);
        }

        @Override
        public String toString(SendToAddress item, String format) {
            return ((StringConverterWithFormat<SendToAddress>)converter).toStringFormat(item, format);
        }
    };

    public static class SendToAddress {
        private final String hrn;
        private final Address address;
        private final SilentPaymentAddress silentPaymentAddress;

        public SendToAddress(String hrn) {
            this.hrn = hrn;
            this.address = null;
            this.silentPaymentAddress = null;
        }

        public SendToAddress(Address address) {
            this.hrn = null;
            this.address = address;
            this.silentPaymentAddress = null;
        }

        public SendToAddress(SilentPaymentAddress silentPaymentAddress) {
            this.hrn = null;
            this.address = null;
            this.silentPaymentAddress = silentPaymentAddress;
        }

        public String toString() {
            return hrn == null ? silentPaymentAddress == null ? (address == null ? null : address.toString()) : silentPaymentAddress.toString() : hrn;
        }

        public static SendToAddress fromPayment(Payment payment) {
            DnsPayment dnsPayment = DnsPaymentCache.getDnsPayment(payment);
            if(dnsPayment != null) {
                return new SendToAddress(dnsPayment.hrn());
            }
            return payment instanceof SilentPayment ? new SendToAddress(((SilentPayment)payment).getSilentPaymentAddress()) : new SendToAddress(payment.getAddress());
        }

        public Payment toPayment(String label, long value, boolean sendMax) throws DnsPaymentValidationException, IOException, ExecutionException, InterruptedException, BitcoinURIParseException {
            if(hrn != null) {
                DnsPayment dnsPayment = DnsPaymentCache.getDnsPayment(hrn);
                if(dnsPayment == null) {
                    DnsPaymentResolver resolver = new DnsPaymentResolver(hrn);
                    Optional<DnsPayment> optDnsPayment = resolver.resolve();
                    if(optDnsPayment.isPresent()) {
                        dnsPayment = optDnsPayment.get();
                        if(dnsPayment.hasAddress()) {
                            DnsPaymentCache.putDnsPayment(dnsPayment.bitcoinURI().getAddress(), dnsPayment);
                        } else if(dnsPayment.hasSilentPaymentAddress()) {
                            DnsPaymentCache.putDnsPayment(dnsPayment.bitcoinURI().getSilentPaymentAddress(), dnsPayment);
                        }
                        return getPayment(optDnsPayment.get(), label, value, sendMax);
                    } else {
                        throw new IllegalArgumentException("Payment to " + hrn + " could not be resolved.");
                    }
                } else {
                    return getPayment(dnsPayment, label, value, sendMax);
                }
            }

            if(silentPaymentAddress != null) {
                return new SilentPayment(silentPaymentAddress, label, value, sendMax);
            } else {
                return new Payment(address, label, value, sendMax);
            }
        }

        private static Payment getPayment(DnsPayment dnsPayment, String label, long value, boolean sendMax) {
            if(dnsPayment.hasAddress()) {
                return new Payment(dnsPayment.bitcoinURI().getAddress(), label, value, sendMax);
            } else if(dnsPayment.hasSilentPaymentAddress()) {
                return new SilentPayment(dnsPayment.bitcoinURI().getSilentPaymentAddress(), label, value, sendMax);
            } else {
                throw new IllegalArgumentException("Payment to " + dnsPayment + " has no associated address.");
            }
        }
    }

    private static class SendToAddressStringConverter extends StringConverter<SendToAddress> {
        private final AddressStringConverter addressStringConverter = new AddressStringConverter();

        @Override
        public SendToAddress fromString(String value) {
            Optional<String> optDnsPaymentHrn = DnsPayment.getHrn(value);
            if(optDnsPaymentHrn.isPresent()) {
                return new SendToAddress(optDnsPaymentHrn.get());
            }

            try {
                SilentPaymentAddress silentPaymentAddress = SilentPaymentAddress.from(value);
                return new SendToAddress(silentPaymentAddress);
            } catch(Exception e) {
                Address address = addressStringConverter.fromString(value);
                return address == null ? null : new SendToAddress(address);
            }
        }

        @Override
        public String toString(SendToAddress value) {
            return value.toString();
        }
    }

    private class CreatePaymentsService extends Service<List<Payment>> {
        @Override
        protected Task<List<Payment>> createTask() {
            return new Task<>() {
                @Override
                protected List<Payment> call() throws Exception {
                    return getPayments();
                }
            };
        }

        private List<Payment> getPayments() throws DnsPaymentValidationException, IOException, ExecutionException, InterruptedException, BitcoinURIParseException {
            List<Payment> payments = new ArrayList<>();
            Grid grid = spreadsheetView.getGrid();
            String firstLabel = null;
            for(int row = 0; row < grid.getRowCount(); row++) {
                ObservableList<SpreadsheetCell> rowCells = spreadsheetView.getItems().get(row);
                SendToAddress sendToAddress = (SendToAddress)rowCells.get(0).getItem();
                Double value = (Double)rowCells.get(1).getItem();
                String label = (String)rowCells.get(2).getItem();
                if(firstLabel == null) {
                    firstLabel = label;
                }
                if(label == null || label.isEmpty()) {
                    label = firstLabel;
                }

                if(sendToAddress != null && value != null) {
                    if(bitcoinUnit == BitcoinUnit.BTC) {
                        value = value * Transaction.SATOSHIS_PER_BITCOIN;
                    }

                    payments.add(sendToAddress.toPayment(label, value.longValue(), false));
                }
            }

            return payments;
        }
    }

    private record SendToPayment(Payment payment, SendToAddress sendToAddress) {}
}
