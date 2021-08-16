package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.drongo.Utils;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.glyphfont.FontAwesome5;
import javafx.application.Platform;
import javafx.beans.NamedArg;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import org.controlsfx.glyphfont.Glyph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

public class TextAreaDialog extends Dialog<String> {
    private static final Logger log = LoggerFactory.getLogger(TextAreaDialog.class);

    private final TextArea textArea;
    private final String defaultValue;

    public TextAreaDialog() {
        this("");
    }

    public TextAreaDialog(String defaultValue) {
        this(defaultValue, true);
    }

    public TextAreaDialog(@NamedArg("defaultValue") String defaultValue, @NamedArg("editable") boolean editable) {
        final DialogPane dialogPane = new TextAreaDialogPane();
        setDialogPane(dialogPane);

        Image image = new Image("/image/sparrow-small.png");
        dialogPane.setGraphic(new ImageView(image));

        HBox hbox = new HBox();
        this.textArea = new TextArea(defaultValue);
        this.textArea.setMaxWidth(Double.MAX_VALUE);
        this.textArea.setWrapText(true);
        this.textArea.getStyleClass().add("fixed-width");
        this.textArea.setEditable(editable);
        hbox.getChildren().add(textArea);
        HBox.setHgrow(this.textArea, Priority.ALWAYS);

        this.defaultValue = defaultValue;

        dialogPane.setContent(hbox);
        dialogPane.getStylesheets().add(AppServices.class.getResource("general.css").toExternalForm());
        AppServices.setStageIcon(dialogPane.getScene().getWindow());

        dialogPane.getStyleClass().add("text-input-dialog");
        dialogPane.getButtonTypes().add(ButtonType.OK);
        if(editable) {
            dialogPane.getButtonTypes().add(ButtonType.CANCEL);

            final ButtonType scanButtonType = new javafx.scene.control.ButtonType("Scan QR", ButtonBar.ButtonData.LEFT);
            dialogPane.getButtonTypes().add(scanButtonType);
        }

        Platform.runLater(textArea::requestFocus);

        setResultConverter((dialogButton) -> {
            ButtonBar.ButtonData data = dialogButton == null ? null : dialogButton.getButtonData();
            return data == ButtonBar.ButtonData.OK_DONE ? textArea.getText() : null;
        });

        dialogPane.setPrefWidth(700);
        dialogPane.setPrefHeight(400);
        AppServices.moveToActiveWindowScreen(this);
    }

    public final TextArea getEditor() {
        return textArea;
    }

    public final String getDefaultValue() {
        return defaultValue;
    }

    private class TextAreaDialogPane extends DialogPane {
        @Override
        protected Node createButton(ButtonType buttonType) {
            Node button;
            if(buttonType.getButtonData() == ButtonBar.ButtonData.LEFT) {
                Button scanButton = new Button(buttonType.getText());
                scanButton.setGraphicTextGap(5);
                scanButton.setGraphic(getGlyph(FontAwesome5.Glyph.CAMERA));

                final ButtonBar.ButtonData buttonData = buttonType.getButtonData();
                ButtonBar.setButtonData(scanButton, buttonData);
                scanButton.setOnAction(event -> {
                    QRScanDialog qrScanDialog = new QRScanDialog();
                    Optional<QRScanDialog.Result> optionalResult = qrScanDialog.showAndWait();
                    if(optionalResult.isPresent()) {
                        QRScanDialog.Result result = optionalResult.get();
                        if(result.payload != null) {
                            textArea.setText(result.payload);
                        } else if(result.psbt != null) {
                            textArea.setText(result.psbt.toBase64String());
                        } else if(result.transaction != null) {
                            textArea.setText(Utils.bytesToHex(result.transaction.bitcoinSerialize()));
                        } else if(result.uri != null) {
                            textArea.setText(result.uri.toString());
                        } else if(result.extendedKey != null) {
                            textArea.setText(result.extendedKey.getExtendedKey());
                        } else if(result.outputDescriptor != null) {
                            textArea.setText(result.outputDescriptor.toString(true));
                        } else if(result.exception != null) {
                            log.error("Error scanning QR", result.exception);
                            AppServices.showErrorDialog("Error scanning QR", result.exception.getMessage());
                        }
                    }
                });

                button = scanButton;
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
