package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.drongo.wallet.Bip39MnemonicCode;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.glyphfont.FontAwesome5;
import com.sparrowwallet.sparrow.io.PdfUtils;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;
import org.controlsfx.control.spreadsheet.*;
import org.controlsfx.glyphfont.Glyph;
import org.controlsfx.tools.Platform;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;


public class MnemonicGridDialog extends Dialog<List<String>> {
    private final SpreadsheetView spreadsheetView;

    private final BooleanProperty initializedProperty = new SimpleBooleanProperty(false);
    private final BooleanProperty wordsSelectedProperty = new SimpleBooleanProperty(false);

    public MnemonicGridDialog() {
        DialogPane dialogPane = new MnemonicGridDialogPane();
        setDialogPane(dialogPane);
        setTitle("Border Wallets Grid");
        dialogPane.getStylesheets().add(AppServices.class.getResource("general.css").toExternalForm());
        dialogPane.getStylesheets().add(AppServices.class.getResource("grid.css").toExternalForm());
        dialogPane.setHeaderText("Load a Border Wallets PDF, and select 11 or 23 words in the grid.\nThe order of selection is important!");
        javafx.scene.image.Image image = new Image("/image/border-wallets.png");
        dialogPane.setGraphic(new ImageView(image));

        String[][] emptyWordGrid = new String[128][16];
        Grid grid = getGrid(emptyWordGrid);

        spreadsheetView = new SpreadsheetView(grid);
        spreadsheetView.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
            try {
                Field f = event.getClass().getDeclaredField(Platform.getCurrent() == Platform.OSX ? "metaDown" : "controlDown");
                f.setAccessible(true);
                f.set(event, true);
            } catch(IllegalAccessException | NoSuchFieldException e) {
                //ignore
            }
        });
        spreadsheetView.setId("grid");
        spreadsheetView.setEditable(false);
        spreadsheetView.setFixingColumnsAllowed(false);
        spreadsheetView.setFixingRowsAllowed(false);

        spreadsheetView.getSelectionModel().getSelectedCells().addListener(new ListChangeListener<>() {
            @Override
            public void onChanged(Change<? extends TablePosition> c) {
                int numWords = c.getList().size();
                wordsSelectedProperty.set(numWords == 11 || numWords == 23);
            }
        });

        StackPane stackPane = new StackPane();
        stackPane.getChildren().add(spreadsheetView);
        dialogPane.setContent(stackPane);

        stackPane.widthProperty().addListener((observable, oldValue, newValue) -> {
            if(newValue != null) {
                for(SpreadsheetColumn column : spreadsheetView.getColumns()) {
                    column.setPrefWidth((newValue.doubleValue() - spreadsheetView.getRowHeaderWidth() - 3) / 17);
                }
            }
        });

        dialogPane.getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        final ButtonType loadCsvButtonType = new javafx.scene.control.ButtonType("Load PDF", ButtonBar.ButtonData.LEFT);
        dialogPane.getButtonTypes().add(loadCsvButtonType);

        Button okButton = (Button)dialogPane.lookupButton(ButtonType.OK);
        okButton.disableProperty().bind(Bindings.not(Bindings.and(initializedProperty, wordsSelectedProperty)));

        setResultConverter((dialogButton) -> {
            ButtonBar.ButtonData data = dialogButton == null ? null : dialogButton.getButtonData();
            return data == ButtonBar.ButtonData.OK_DONE ? getSelectedWords() : null;
        });

        dialogPane.setPrefWidth(850);
        dialogPane.setPrefHeight(500);
        AppServices.setStageIcon(dialogPane.getScene().getWindow());
        AppServices.moveToActiveWindowScreen(this);
    }

    private Grid getGrid(String[][] wordGrid) {
        int rowCount = wordGrid.length;
        int columnCount = wordGrid[0].length;
        GridBase grid = new GridBase(rowCount, columnCount);
        ObservableList<ObservableList<SpreadsheetCell>> rows = FXCollections.observableArrayList();
        grid.getColumnHeaders().setAll("A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M", "N", "O", "P");
        for(int i = 0; i < rowCount; i++) {
            final ObservableList<SpreadsheetCell> list = FXCollections.observableArrayList();
            for(int j = 0; j < columnCount; j++) {
                list.add(createCell(i, j, wordGrid[i][j]));
            }
            rows.add(list);
            grid.getRowHeaders().add(String.format("%03d", i + 1));
        }
        grid.setRows(rows);

        return grid;
    }

    private SpreadsheetCell createCell(int row, int column, String word) {
        return SpreadsheetCellType.STRING.createCell(row, column, 1, 1, word == null ? "" : word);
    }

    private List<String> getSelectedWords() {
        List<String> abbreviations = spreadsheetView.getSelectionModel().getSelectedCells().stream()
                .map(position -> (String)spreadsheetView.getGrid().getRows().get(position.getRow()).get(position.getColumn()).getItem()).collect(Collectors.toList());

        List<String> words = new ArrayList<>();
        for(String abbreviation : abbreviations) {
            for(String word : Bip39MnemonicCode.INSTANCE.getWordList()) {
                if((abbreviation.length() == 3 && word.equals(abbreviation)) || (abbreviation.length() >= 4 && word.startsWith(abbreviation))) {
                    words.add(word);
                    break;
                }
            }
        }

        if(words.size() != abbreviations.size()) {
            abbreviations.removeIf(abbr -> words.stream().anyMatch(w -> w.startsWith(abbr)));
            throw new IllegalStateException("Could not find words for abbreviations: " + abbreviations);
        }

        return words;
    }

    private class MnemonicGridDialogPane extends DialogPane {
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
                    fileChooser.setTitle("Open PDF");
                    fileChooser.getExtensionFilters().addAll(
                            new FileChooser.ExtensionFilter("All Files", org.controlsfx.tools.Platform.getCurrent().equals(org.controlsfx.tools.Platform.UNIX) ? "*" : "*.*"),
                            new FileChooser.ExtensionFilter("PDF", "*.pdf")
                    );

                    AppServices.moveToActiveWindowScreen(this.getScene().getWindow(), 800, 450);
                    File file = fileChooser.showOpenDialog(this.getScene().getWindow());
                    if(file != null) {
                        try(BufferedInputStream inputStream = new BufferedInputStream(new FileInputStream(file))) {
                            String[][] wordGrid = PdfUtils.getWordGrid(inputStream);
                            spreadsheetView.setGrid(getGrid(wordGrid));
                            initializedProperty.set(true);
                        } catch(Exception e) {
                            AppServices.showErrorDialog("Cannot load PDF", e.getMessage());
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
}