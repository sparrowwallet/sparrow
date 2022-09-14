package com.sparrowwallet.sparrow.io;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageConfig;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.pdf.PdfWriter;
import com.sparrowwallet.hummingbird.UR;
import com.sparrowwallet.hummingbird.UREncoder;
import com.sparrowwallet.sparrow.AppServices;
import javafx.embed.swing.SwingFXUtils;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.io.*;

public class PdfUtils {
    private static final Logger log = LoggerFactory.getLogger(PdfUtils.class);

    private static final int QR_WIDTH = 480;
    private static final int QR_HEIGHT = 480;

    public static void saveOutputDescriptor(String walletName, String outputDescriptor, UR ur) {
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

    private static javafx.scene.image.Image getQrCode(String fragment) throws IOException, WriterException {
        QRCodeWriter qrCodeWriter = new QRCodeWriter();
        BitMatrix qrMatrix = qrCodeWriter.encode(fragment, BarcodeFormat.QR_CODE, QR_WIDTH, QR_HEIGHT);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        MatrixToImageWriter.writeToStream(qrMatrix, "PNG", baos, new MatrixToImageConfig());

        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        return new javafx.scene.image.Image(bais);
    }
}
