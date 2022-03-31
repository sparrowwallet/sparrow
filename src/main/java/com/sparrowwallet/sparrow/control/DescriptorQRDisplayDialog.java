package com.sparrowwallet.sparrow.control;

import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.pdf.PdfWriter;
import com.sparrowwallet.hummingbird.UR;
import com.sparrowwallet.hummingbird.UREncoder;
import com.sparrowwallet.sparrow.AppServices;
import javafx.embed.swing.SwingFXUtils;
import javafx.event.ActionEvent;
import javafx.scene.control.*;
import javafx.scene.control.Button;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.io.File;
import java.io.FileOutputStream;

public class DescriptorQRDisplayDialog extends QRDisplayDialog {
    private static final Logger log = LoggerFactory.getLogger(DescriptorQRDisplayDialog.class);

    public DescriptorQRDisplayDialog(String walletName, String outputDescriptor, UR ur) {
        super(ur);

        DialogPane dialogPane = getDialogPane();
        final ButtonType pdfButtonType = new javafx.scene.control.ButtonType("Save PDF...", ButtonBar.ButtonData.HELP_2);
        dialogPane.getButtonTypes().add(pdfButtonType);

        Button pdfButton = (Button)dialogPane.lookupButton(pdfButtonType);
        pdfButton.addEventFilter(ActionEvent.ACTION, event -> {
            savePdf(walletName, outputDescriptor, ur);
            event.consume();
        });
    }

    private void savePdf(String walletName, String outputDescriptor, UR ur) {
        Stage window = new Stage();
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save PDF");
        fileChooser.setInitialFileName(walletName + ".pdf");
        AppServices.moveToActiveWindowScreen(window, 800, 450);
        File file = fileChooser.showSaveDialog(window);
        if(file != null) {
            try(Document document = new Document()) {
                document.setMargins(36, 36, 48, 36);
                PdfWriter.getInstance(document, new FileOutputStream(file));
                document.open();

                Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, Color.BLACK);
                Chunk title = new Chunk("Output descriptor for " + walletName, titleFont);
                document.add(title);

                UREncoder urEncoder = new UREncoder(ur, 2000, 10, 0);
                String fragment = urEncoder.nextPart();
                if(urEncoder.isSinglePart()) {
                    Image image = Image.getInstance(SwingFXUtils.fromFXImage(getQrCode(fragment), null), Color.WHITE);
                    document.add(image);
                }

                Font descriptorFont = FontFactory.getFont(FontFactory.COURIER, 14, Color.BLACK);
                Paragraph descriptor = new Paragraph(outputDescriptor, descriptorFont);
                document.add(descriptor);
            } catch(Exception e) {
                log.error("Error creating descriptor PDF", e);
                AppServices.showErrorDialog("Error creating PDF", e.getMessage());
            }
        }
    }
}
