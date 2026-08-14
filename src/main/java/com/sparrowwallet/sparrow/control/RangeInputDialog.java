package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.glyphfont.FontAwesome5;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import org.controlsfx.glyphfont.Glyph;

public class RangeInputDialog extends Dialog<Integer> {
    private final Spinner<Integer> spinner;

    public RangeInputDialog(int min, int max, int initialValue) {
        final DialogPane dialogPane = getDialogPane();

        setTitle("Select a Value");
        setHeaderText("Choose a value between " + min + " and " + max);

        Glyph key = new Glyph(FontAwesome5.FONT_NAME, FontAwesome5.Glyph.SORT_NUMERIC_DOWN);
        key.setFontSize(50);
        key.setPadding(new Insets(0, 0, 0, 10));
        dialogPane.setGraphic(key);
        dialogPane.getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        spinner = new Spinner<>();
        spinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(min, max, initialValue));
        spinner.setPrefWidth(80);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 20, 10, 20));

        grid.add(new Label("Enter value between " + min + " and " + max + ":"), 0, 0);
        grid.add(spinner, 1, 0);

        dialogPane.setContent(grid);
        dialogPane.getStylesheets().add(AppServices.class.getResource("general.css").toExternalForm());
        AppServices.setStageIcon(dialogPane.getScene().getWindow());

        Platform.runLater(spinner::requestFocus);

        setResultConverter((dialogButton) -> {
            ButtonBar.ButtonData data = dialogButton == null ? null : dialogButton.getButtonData();
            return data == ButtonBar.ButtonData.OK_DONE ? spinner.getValue() : null;
        });

        dialogPane.setPrefWidth(500);
        dialogPane.setPrefHeight(230);
        AppServices.moveToActiveWindowScreen(this);
    }

    public void setValue(int value) {
        spinner.getValueFactory().setValue(value);
    }

    public int getValue() {
        return spinner.getValue();
    }
}
