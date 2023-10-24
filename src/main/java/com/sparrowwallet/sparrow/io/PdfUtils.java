package com.sparrowwallet.sparrow.io;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageConfig;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.Rectangle;
import com.lowagie.text.alignment.HorizontalAlignment;
import com.lowagie.text.alignment.VerticalAlignment;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.PdfWriter;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import com.sparrowwallet.drongo.OutputDescriptor;
import com.sparrowwallet.drongo.Utils;
import com.sparrowwallet.drongo.protocol.ScriptType;
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
import java.util.*;
import java.util.List;

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

    public static OutputDescriptor getOutputDescriptor(InputStream inputStream) throws IOException {
        try {
            PdfReader pdfReader = new PdfReader(inputStream);
            String allText = "";
            for(int page = 1; page <= pdfReader.getNumberOfPages(); page++) {
                PdfTextExtractor textExtractor = new PdfTextExtractor(pdfReader);
                allText += textExtractor.getTextFromPage(page) + "\n";
            }

            String descriptor = null;
            Scanner scanner = new Scanner(allText);
            while(scanner.hasNextLine()) {
                String line = scanner.nextLine().trim();
                if(descriptor != null) {
                    descriptor += line;
                } else if(ScriptType.fromDescriptor(line) != null) {
                    descriptor = line;
                }
            }

            if(descriptor != null) {
                return OutputDescriptor.getOutputDescriptor(descriptor);
            }
        } catch(Exception e) {
            throw new IllegalArgumentException("Not a valid PDF or output descriptor");
        }

        throw new IllegalArgumentException("Output descriptor could not be found");
    }

    private static javafx.scene.image.Image getQrCode(String fragment) throws IOException, WriterException {
        QRCodeWriter qrCodeWriter = new QRCodeWriter();
        BitMatrix qrMatrix = qrCodeWriter.encode(fragment.toUpperCase(Locale.ROOT), BarcodeFormat.QR_CODE, QR_WIDTH, QR_HEIGHT, Map.of(EncodeHintType.MARGIN, "2"));

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        MatrixToImageWriter.writeToStream(qrMatrix, "PNG", baos, new MatrixToImageConfig());

        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        return new javafx.scene.image.Image(bais);
    }

    public static String[][] getWordGrid(InputStream inputStream) {
        try {
            PdfReader pdfReader = new PdfReader(inputStream);
            String allText = "";
            for(int page = 1; page <= pdfReader.getNumberOfPages(); page++) {
                PdfTextExtractor textExtractor = new PdfTextExtractor(pdfReader);
                allText += textExtractor.getTextFromPage(page) + "\n";
            }

            List<String[]> rows = new ArrayList<>();
            Scanner scanner = new Scanner(allText);
            while(scanner.hasNextLine()) {
                String line = scanner.nextLine().trim();
                String[] words = line.split(" ");
                if(words.length > 16 && Utils.isNumber(words[0])) {
                    rows.add(Arrays.copyOfRange(words, 1, 17));
                }
            }

            if(rows.size() < 128) {
                throw new IllegalArgumentException("Not a valid Border Wallets PDF");
            }

            return rows.toArray(new String[][]{new String[0]});
        } catch(Exception e) {
            throw new IllegalArgumentException("Not a valid Border Wallets PDF");
        }
    }

    public static void saveWordGrid(String[][] wordGrid, List<String> mnemonicWords) {
        Stage window = new Stage();
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Border Wallet Grid");
        fileChooser.setInitialFileName("BorderWalletEntropyGrid.pdf");
        AppServices.moveToActiveWindowScreen(window, 800, 450);
        File file = fileChooser.showSaveDialog(window);
        if(file != null) {
            try(Document document = new Document()) {
                document.setMargins(0, 0, 10, 10);
                PdfWriter.getInstance(document, new FileOutputStream(file));

                Font headerFont = new Font(Font.HELVETICA, 8, Font.BOLD, Color.DARK_GRAY);
                Font font = new Font(Font.HELVETICA, 8, Font.NORMAL, Color.DARK_GRAY);

                HeaderFooter footer = new HeaderFooter(false, new Phrase("Recovery Phrase (to regenerate grid): " + String.join(" ", mnemonicWords), font));
                footer.setAlignment(Element.ALIGN_CENTER);
                footer.setBorder(Rectangle.NO_BORDER);
                footer.setBorderWidth(0);
                document.setFooter(footer);

                document.open();

                int rowCount = wordGrid.length;
                int columnCount = wordGrid[0].length;

                Table table = new Table(columnCount + 2);
                table.setBorderWidth(0.5f);
                table.setBorderColor(new Color(208, 208, 208));
                table.setBorderWidthLeft(0);
                table.setBorderWidthRight(0);

                List<String> headers = List.of(" ", "A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M", "N", "O", "P", " ");
                for(String header : headers) {
                    Paragraph paragraph = new Paragraph(header, headerFont);
                    Cell cell = new Cell(paragraph);
                    cell.setBackgroundColor(new Color(218, 218, 218));
                    cell.setHorizontalAlignment(HorizontalAlignment.CENTER);
                    cell.setVerticalAlignment(VerticalAlignment.CENTER);
                    cell.setBorderWidth(0.5f);
                    cell.setBorderColor(new Color(208, 208, 208));
                    cell.setHeader(true);
                    table.addCell(cell);
                }
                table.endHeaders();

                for(int i = 0; i < rowCount; i++) {
                    for(int j = 0; j < columnCount + 2; j++) {
                        Cell cell;
                        if(j == 0 || j == columnCount + 1) {
                            cell = new Cell(new Paragraph(String.format("%03d", i + 1), headerFont));
                            cell.setBackgroundColor(new Color(218, 218, 218));
                        } else {
                            cell = new Cell(new Paragraph(wordGrid[i][j-1], font));
                        }
                        cell.setHorizontalAlignment(HorizontalAlignment.CENTER);
                        cell.setVerticalAlignment(VerticalAlignment.CENTER);
                        cell.setBorderWidth(0.5f);
                        cell.setBorderColor(new Color(208, 208, 208));
                        table.addCell(cell);
                    }
                }

                document.add(table);
            } catch(Exception e) {
                log.error("Error creating word grid PDF", e);
                AppServices.showErrorDialog("Error creating word grid PDF", e.getMessage());
            }
        } else {
            AppServices.showWarningDialog("Entropy Grid PDF not saved", "You have chosen to not save the entropy grid PDF.\n\nDo not store funds on a seed selected from this grid - you will not be able to regenerate it!");
        }
    }
}
