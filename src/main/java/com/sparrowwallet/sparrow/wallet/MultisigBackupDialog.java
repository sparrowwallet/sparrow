package com.sparrowwallet.sparrow.wallet;

import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.hummingbird.UR;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.glyphfont.FontAwesome5;
import com.sparrowwallet.sparrow.io.PdfUtils;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import org.controlsfx.glyphfont.Glyph;

public class MultisigBackupDialog extends Dialog<String> {
    private final Wallet wallet;
    private final String descriptor;
    private final UR ur;

    private final TextArea textArea;

    public MultisigBackupDialog(Wallet wallet, String descriptor, UR ur) {
        this.wallet = wallet;
        this.descriptor = descriptor;
        this.ur = ur;

        setTitle("Backup Multisig Wallet?");

        DialogPane dialogPane = new MultisigBackupDialogPane();
        dialogPane.setHeaderText("To restore this multisig wallet, you need at least " + wallet.getDefaultPolicy().getNumSignaturesRequired() + " seeds and ALL of the xpubs! " +
                "For the xpubs, it is recommended to backup either this wallet file, or the wallet output descriptor.\n\n" +
                "The wallet output descriptor contains all " + wallet.getKeystores().size() + " of the xpubs and is shown below. " +
                "Alternatively, use the Export button below to export the Sparrow wallet file.");
        setDialogPane(dialogPane);

        dialogPane.getStyleClass().addAll("alert", "warning");

        HBox hbox = new HBox();
        this.textArea = new TextArea(descriptor);
        this.textArea.setMaxWidth(Double.MAX_VALUE);
        this.textArea.setWrapText(true);
        this.textArea.getStyleClass().add("fixed-width");
        this.textArea.setEditable(false);
        hbox.getChildren().add(textArea);
        HBox.setHgrow(this.textArea, Priority.ALWAYS);

        dialogPane.setContent(hbox);
        dialogPane.getStylesheets().add(AppServices.class.getResource("general.css").toExternalForm());
        AppServices.setStageIcon(dialogPane.getScene().getWindow());

        dialogPane.getStyleClass().add("text-input-dialog");
        dialogPane.getButtonTypes().add(ButtonType.OK);

        final ButtonType qrButtonType = new javafx.scene.control.ButtonType("Save PDF...", ButtonBar.ButtonData.LEFT);
        dialogPane.getButtonTypes().add(qrButtonType);

        dialogPane.setPrefWidth(700);
        dialogPane.setPrefHeight(500);
        dialogPane.setMinHeight(dialogPane.getPrefHeight());
        AppServices.moveToActiveWindowScreen(this);
    }

    private class MultisigBackupDialogPane extends DialogPane {
        @Override
        protected Node createButton(ButtonType buttonType) {
            Node button;
            if(buttonType.getButtonData() == ButtonBar.ButtonData.LEFT) {
                Button pdfButton = new Button(buttonType.getText());
                pdfButton.setGraphicTextGap(5);
                pdfButton.setGraphic(getGlyph(FontAwesome5.Glyph.FILE_PDF));

                final ButtonBar.ButtonData buttonData = buttonType.getButtonData();
                ButtonBar.setButtonData(pdfButton, buttonData);
                pdfButton.setOnAction(event -> {
                    PdfUtils.saveOutputDescriptor(wallet.getFullDisplayName(), descriptor, ur);
                });

                button = pdfButton;
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
