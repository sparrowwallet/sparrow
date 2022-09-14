package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.hummingbird.UR;
import com.sparrowwallet.sparrow.glyphfont.FontAwesome5;
import com.sparrowwallet.sparrow.io.PdfUtils;
import javafx.event.ActionEvent;
import javafx.scene.control.*;
import javafx.scene.control.Button;

public class DescriptorQRDisplayDialog extends QRDisplayDialog {
    public DescriptorQRDisplayDialog(String walletName, String outputDescriptor, UR ur) {
        super(ur);

        DialogPane dialogPane = getDialogPane();
        final ButtonType pdfButtonType = new javafx.scene.control.ButtonType("Save PDF...", ButtonBar.ButtonData.HELP_2);
        dialogPane.getButtonTypes().add(pdfButtonType);

        Button pdfButton = (Button)dialogPane.lookupButton(pdfButtonType);
        pdfButton.setGraphicTextGap(5);
        pdfButton.setGraphic(getGlyph(FontAwesome5.Glyph.FILE_PDF));
        pdfButton.addEventFilter(ActionEvent.ACTION, event -> {
            PdfUtils.saveOutputDescriptor(walletName, outputDescriptor, ur);
            event.consume();
        });
    }
}
