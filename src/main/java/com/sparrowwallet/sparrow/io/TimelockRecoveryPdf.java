package com.sparrowwallet.sparrow.io;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageConfig;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;
import com.sparrowwallet.drongo.protocol.Transaction;
import com.sparrowwallet.drongo.protocol.TransactionOutput;
import com.sparrowwallet.drongo.wallet.Payment;
import com.sparrowwallet.sparrow.SparrowWallet;
import com.sparrowwallet.sparrow.UnitFormat;
import com.sparrowwallet.sparrow.timelockrecovery.TimelockRecovery;
import com.sparrowwallet.sparrow.timelockrecovery.TimelockRecoveryPlan;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

public class TimelockRecoveryPdf {
    private static final int TX_QR_SIZE = 280;
    private static final int LINK_QR_SIZE = 150;
    static final int TX_QR_MARGIN = 1;
    private static final int LOGO_SIZE = 80;
    static final int HEX_CHARS_PER_LINE = TimelockRecovery.QR_HEX_LINE_LENGTH;
    private static final DateTimeFormatter HEADER_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'").withZone(ZoneOffset.UTC);

    private TimelockRecoveryPdf() {
    }

    public static void writeRecoveryGuide(File file, TimelockRecovery recovery, TimelockRecoveryPlan plan, Instant createdAt) throws IOException {
        String id = String.valueOf(plan.getFields().get("id"));
        Transaction initiationTx = recovery.getSignedInitiationTx();
        Transaction recoveryTx = recovery.getSignedRecoveryTx();
        String walletName = recovery.getWallet().getFullDisplayName();
        int days = recovery.getTimelockDays();

        try(Document document = openDocument(file, "Recovery-Guide", createdAt, id)) {
            addLogo(document);
            addTitle(document, "Timelock-Recovery Guide");
            addSubtitle(document, "Sparrow Version: " + SparrowWallet.APP_VERSION);

            String destWord = recoveryTx.getOutputs().size() == 1 ? "address" : "addresses";
            StringBuilder intro = new StringBuilder();
            intro.append("This document will guide you through the process of recovering the funds on wallet: ").append(walletName).append(". ");
            intro.append("The process will take at least ").append(days).append(" days, and will eventually send the following amount ");
            intro.append("to the following ").append(destWord).append(":\n\n");
            List<Payment> payments = recovery.getRecoveryWalletTx().getPayments();
            for(int i = 0; i < recoveryTx.getOutputs().size(); i++) {
                TransactionOutput output = recoveryTx.getOutputs().get(i);
                intro.append("• ").append(output.getScript().getToAddress()).append(": ").append(formatAmount(output.getValue()));
                if(i < payments.size() && payments.get(i).getLabel() != null && !payments.get(i).getLabel().isBlank()) {
                    intro.append(" (").append(payments.get(i).getLabel()).append(")");
                }
                intro.append("\n");
            }
            intro.append("\nBefore proceeding, MAKE SURE THAT YOU HAVE ACCESS TO THE ");
            intro.append(recoveryTx.getOutputs().size() == 1 ? "WALLET OF THIS ADDRESS" : "WALLETS OF THESE ADDRESSES");
            intro.append(", OR TRUST THE ");
            intro.append(recoveryTx.getOutputs().size() == 1 ? "OWNER OF THIS ADDRESS" : "OWNERS OF THESE ADDRESSES");
            intro.append(". The simplest way to do so is to send a small amount to the address, and then trying ");
            intro.append("to send all funds from that wallet to a different wallet. Also important: make sure that the ");
            intro.append("seed-phrase of this wallet has not been compromised, or else a malicious actor could steal ");
            intro.append("the funds the moment they reach their destination.\n\n");
            intro.append("For more information, visit: ").append(TimelockRecovery.SITE_URL).append("\n");
            addBody(document, intro.toString());

            addSmallTitle(document, "Step 1 - Broadcasting the Initiation transaction");
            String initiationHex = TimelockRecovery.toUpperHex(initiationTx);
            List<String> initiationParts = TimelockRecovery.splitHexForQr(initiationHex);
            StringBuilder step1 = new StringBuilder();
            step1.append("The first step is to broadcast the Initiation transaction. ");
            step1.append("This transaction will keep most funds in the same wallet ").append(walletName).append(", ");
            List<String> anchors = recovery.getAnchorAddresses().stream().map(Object::toString).toList();
            if(!anchors.isEmpty()) {
                step1.append("except for 600 sats that will be sent to ");
                step1.append(anchors.size() > 1 ? "each of the following addresses" : "the following address");
                step1.append(" (and can be used in case you need to accelerate the transaction via Child-Pays-For-Parent, as we'll explain later):\n");
                for(String address : anchors) {
                    step1.append("• ").append(address).append("\n");
                }
            } else {
                step1.append("except for a small fee.\n");
            }
            step1.append("\nTo broadcast the Initiation transaction, ");
            if(initiationParts.size() <= 1) {
                step1.append("scan the QR code on the next page");
            } else {
                step1.append("scan the QR codes on the next ").append(initiationParts.size()).append(" pages, concatenate the contents of the QR codes (without spaces)");
            }
            step1.append(", and paste the content in one of the following Bitcoin block-explorer websites:\n");
            step1.append("• ").append(TimelockRecovery.mempoolPushUrl()).append("\n");
            step1.append("• ").append(TimelockRecovery.blockstreamPushUrl()).append("\n");
            step1.append("• ").append(TimelockRecovery.coinbinBroadcastUrl()).append("\n\n");
            step1.append("You should then see a success message for broadcasting transaction-id:\n").append(initiationTx.getTxId());
            addBody(document, step1.toString());

            for(int i = 0; i < initiationParts.size(); i++) {
                document.newPage();
                addTitle(document, "Initiation Transaction");
                addSubtitle(document, "Transaction Id: " + initiationTx.getTxId());
                if(initiationParts.size() > 1) {
                    addSubtitle(document, "Part " + (i + 1) + " of " + initiationParts.size());
                }
                addQr(document, initiationParts.get(i), ErrorCorrectionLevel.Q, TX_QR_SIZE, TX_QR_MARGIN);
                addHex(document, initiationParts.get(i));
            }

            document.newPage();
            addSmallTitle(document, "Step 2 - Waiting for the Initiation transaction confirmation");
            addBody(document, "You can follow the Initiation transaction via any of the following links:");
            String initiationTxid = initiationTx.getTxId().toString();
            addLinkWithQr(document, TimelockRecovery.mempoolTxUrl(initiationTxid));
            addLinkWithQr(document, TimelockRecovery.blockstreamTxUrl(initiationTxid));

            StringBuilder step2 = new StringBuilder();
            step2.append("Please wait for a while until the transaction is marked as \"confirmed\" (number of confirmations greater than 0). ");
            step2.append("The time that takes a transaction to confirm depends on the fee that it pays, compared to the fee that other ");
            step2.append("pending transactions are willing to pay. At the time this document was created, it was hard to predict what a ");
            step2.append("reasonable fee would be today. If the transaction is not confirmed after 24 hours, you may try paying to a ");
            step2.append("Transaction Acceleration service, such as the one offered by: https://mempool.space .");
            if(!anchors.isEmpty()) {
                step2.append(" Another solution, which may be cheaper but requires more technical skill, would be to use");
                step2.append(anchors.size() > 1 ? " one of the wallets that receive 600 sats (addresses mentioned in Step 1)," : " the wallet that receive 600 sats (address mentioned in Step 1),");
                step2.append(" and send a high-fee transaction that includes that 600 sats UTXO (this transaction could also be from the");
                step2.append(" wallet to itself). For more information, visit: ").append(TimelockRecovery.SITE_URL).append(" .");
            }
            addBody(document, step2.toString());

            addSmallTitle(document, "Step 3 - Broadcasting the Recovery transaction");
            String recoveryHex = TimelockRecovery.toUpperHex(recoveryTx);
            List<String> recoveryParts = TimelockRecovery.splitHexForQr(recoveryHex);
            StringBuilder step3 = new StringBuilder();
            step3.append("Approximately ").append(days).append(" days after the Initiation transaction has been confirmed, you ");
            step3.append("will be able to broadcast the second Recovery transaction that will send the funds to the final ");
            step3.append(recoveryTx.getOutputs().size() > 1 ? "destinations," : "destination,");
            step3.append(" mentioned on the first page. This can be done using the same websites mentioned in Step 1, but ");
            if(recoveryParts.size() <= 1) {
                step3.append("this time you will need to scan the QR code on the following page");
            } else {
                step3.append("this time you will need to scan the QR codes on the following pages and concatenate their content (without spaces)");
            }
            step3.append(". If this transaction remains unconfirmed for a long time, you should use the Transaction Acceleration service mentioned on Step 2, or use the Child-Pays-For-Parent technique.");
            addBody(document, step3.toString());

            for(int i = 0; i < recoveryParts.size(); i++) {
                document.newPage();
                addTitle(document, "Recovery Transaction");
                addSubtitle(document, "Transaction Id: " + recoveryTx.getTxId());
                if(recoveryParts.size() > 1) {
                    addSubtitle(document, "Part " + (i + 1) + " of " + recoveryParts.size());
                }
                addQr(document, recoveryParts.get(i), ErrorCorrectionLevel.Q, TX_QR_SIZE, TX_QR_MARGIN);
                addHex(document, recoveryParts.get(i));
            }
        } catch(DocumentException | WriterException e) {
            throw new IOException("Error creating recovery guide PDF", e);
        }
    }

    public static void writeCancellationGuide(File file, TimelockRecovery recovery, Instant createdAt, String id) throws IOException {
        Transaction initiationTx = recovery.getSignedInitiationTx();
        Transaction cancellationTx = recovery.getSignedCancellationTx();
        String walletName = recovery.getWallet().getFullDisplayName();
        int days = recovery.getTimelockDays();
        String cancellationHex = TimelockRecovery.toUpperHex(cancellationTx);
        List<String> cancellationParts = TimelockRecovery.splitHexForQr(cancellationHex);

        try(Document document = openDocument(file, "Cancellation-Guide", createdAt, id)) {
            addLogo(document);
            addTitle(document, "Timelock-Recovery Cancellation Guide");
            addSubtitle(document, "Sparrow Version: " + SparrowWallet.APP_VERSION);

            StringBuilder explanation = new StringBuilder();
            explanation.append("This document is intended solely for the eyes of the owner of wallet: ").append(walletName).append(". ");
            explanation.append("The Recovery Guide (the other document) will allow to transfer the funds from this wallet to ");
            explanation.append("a different wallet within ").append(days).append(" days. To prevent this from happening accidentally ");
            explanation.append("or maliciously by someone who found that document, you should periodically check if the Initiation ");
            explanation.append("transaction has been broadcast, using a Bitcoin block-explorer website such as:");
            addBody(document, explanation.toString());

            String initiationTxid = initiationTx.getTxId().toString();
            addLinkWithQr(document, TimelockRecovery.mempoolTxUrl(initiationTxid));
            addLinkWithQr(document, TimelockRecovery.blockstreamTxUrl(initiationTxid));

            addBody(document, "It is also recommended to use a Watch-Tower service that will notify you immediately if the"
                    + " Initiation transaction has been broadcast. For more details, visit: " + TimelockRecovery.SITE_URL + " .");

            String qrPages = cancellationParts.size() <= 1 ? "the last page" : "the last " + cancellationParts.size() + " pages";
            StringBuilder cancellationText = new StringBuilder();
            cancellationText.append("In case the Initiation transaction has been broadcast, and you want to stop the funds from ");
            cancellationText.append("leaving this wallet, you can scan the QR code");
            cancellationText.append(cancellationParts.size() > 1 ? "s" : "").append(" on ").append(qrPages);
            cancellationText.append(", and broadcast ");
            cancellationText.append("the content using one of the following Bitcoin block-explorer websites:\n\n");
            cancellationText.append("• ").append(TimelockRecovery.mempoolPushUrl()).append("\n");
            cancellationText.append("• ").append(TimelockRecovery.blockstreamPushUrl()).append("\n");
            cancellationText.append("• ").append(TimelockRecovery.coinbinBroadcastUrl()).append("\n\n");
            cancellationText.append("If the transaction is not confirmed within reasonable time due to a low fee, you will have ");
            cancellationText.append("to access the wallet and use Replace-By-Fee/Child-Pays-For-Parent to move the funds to a new ");
            cancellationText.append("address on your wallet. (you can also pay to an Acceleration Service such as the one offered ");
            cancellationText.append("by https://mempool.space)\n\n");
            cancellationText.append("IMPORTANT NOTICE: If you lost the keys to access wallet ").append(walletName);
            cancellationText.append(" - do not broadcast the transaction on ").append(qrPages);
            cancellationText.append("! In this case it is recommended to destroy all copies of this document.");
            addBody(document, cancellationText.toString());

            for(int i = 0; i < cancellationParts.size(); i++) {
                document.newPage();
                addTitle(document, "Cancellation Transaction");
                addSubtitle(document, "Transaction Id: " + cancellationTx.getTxId());
                if(cancellationParts.size() > 1) {
                    addSubtitle(document, "Part " + (i + 1) + " of " + cancellationParts.size());
                }
                addQr(document, cancellationParts.get(i), ErrorCorrectionLevel.Q, TX_QR_SIZE, TX_QR_MARGIN);
                addHex(document, cancellationParts.get(i));
            }
        } catch(DocumentException | WriterException e) {
            throw new IOException("Error creating cancellation guide PDF", e);
        }
    }

    private static Document openDocument(File file, String kind, Instant createdAt, String id) throws IOException, DocumentException {
        Document document = new Document(PageSize.A4, 48, 48, 64, 48);
        PdfWriter writer = PdfWriter.getInstance(document, new FileOutputStream(file));
        writer.setPageEvent(new HeaderEvent(kind, createdAt, id));
        document.open();
        return document;
    }

    private static class HeaderEvent extends PdfPageEventHelper {
        private final String kind;
        private final Instant createdAt;
        private final String id;

        private HeaderEvent(String kind, Instant createdAt, String id) {
            this.kind = kind;
            this.createdAt = createdAt;
            this.id = id;
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            Font font = FontFactory.getFont(FontFactory.HELVETICA, 8, Color.DARK_GRAY);
            String text = kind + " Date: " + HEADER_DATE.format(createdAt) + " ID: " + id + " Page: " + writer.getPageNumber();
            ColumnText.showTextAligned(writer.getDirectContent(), Element.ALIGN_CENTER,
                    new Phrase(text, font), (document.left() + document.right()) / 2, document.top() + 20, 0);
        }
    }

    private static void addLogo(Document document) throws DocumentException, IOException {
        try(InputStream inputStream = TimelockRecoveryPdf.class.getResourceAsStream("/com/sparrowwallet/sparrow/timelockrecovery/timelock_recovery_820.png")) {
            if(inputStream == null) {
                return;
            }
            Image image = Image.getInstance(inputStream.readAllBytes());
            image.scaleToFit(LOGO_SIZE, LOGO_SIZE);
            image.setAlignment(Element.ALIGN_CENTER);
            image.setSpacingAfter(12);
            document.add(image);
        }
    }

    private static void addTitle(Document document, String title) throws DocumentException {
        Font font = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, Color.BLACK);
        Paragraph paragraph = new Paragraph(title, font);
        paragraph.setAlignment(Element.ALIGN_CENTER);
        paragraph.setSpacingAfter(8);
        document.add(paragraph);
    }

    private static void addSmallTitle(Document document, String title) throws DocumentException {
        Font font = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13, Color.BLACK);
        Paragraph paragraph = new Paragraph(title, font);
        paragraph.setSpacingBefore(12);
        paragraph.setSpacingAfter(8);
        document.add(paragraph);
    }

    private static void addSubtitle(Document document, String title) throws DocumentException {
        Font font = FontFactory.getFont(FontFactory.HELVETICA, 10, Color.DARK_GRAY);
        Paragraph paragraph = new Paragraph(title, font);
        paragraph.setAlignment(Element.ALIGN_CENTER);
        paragraph.setSpacingAfter(10);
        document.add(paragraph);
    }

    private static void addBody(Document document, String text) throws DocumentException {
        Font font = FontFactory.getFont(FontFactory.HELVETICA, 10, Color.BLACK);
        Paragraph paragraph = new Paragraph(text, font);
        paragraph.setAlignment(Element.ALIGN_JUSTIFIED);
        paragraph.setSpacingAfter(10);
        document.add(paragraph);
    }

    private static void addHex(Document document, String hex) throws DocumentException {
        Font font = FontFactory.getFont(FontFactory.COURIER, 8, Color.BLACK);
        Paragraph paragraph = new Paragraph(wrapHex(hex), font);
        paragraph.setAlignment(Element.ALIGN_LEFT);
        paragraph.setSpacingBefore(12);
        document.add(paragraph);
    }

    static String wrapHex(String hex) {
        if(hex == null || hex.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for(int i = 0; i < hex.length(); i += HEX_CHARS_PER_LINE) {
            if(i > 0) {
                builder.append('\n');
            }
            builder.append(hex, i, Math.min(i + HEX_CHARS_PER_LINE, hex.length()));
        }
        return builder.toString();
    }

    private static void addQr(Document document, String data, ErrorCorrectionLevel level, int size, int margin) throws IOException, WriterException, DocumentException {
        Image image = Image.getInstance(qrPng(data, level, size, margin));
        image.scaleToFit(size, size);
        image.setAlignment(Element.ALIGN_CENTER);
        image.setSpacingAfter(6);
        document.add(image);
    }

    private static void addLinkWithQr(Document document, String url) throws IOException, WriterException, DocumentException {
        addQr(document, url, ErrorCorrectionLevel.H, LINK_QR_SIZE, 1);
        Font font = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.BLACK);
        Paragraph paragraph = new Paragraph(url, font);
        paragraph.setAlignment(Element.ALIGN_CENTER);
        paragraph.setSpacingBefore(2);
        paragraph.setSpacingAfter(8);
        document.add(paragraph);
    }

    static byte[] qrPng(String data, ErrorCorrectionLevel level, int size, int margin) throws IOException, WriterException {
        QRCodeWriter qrCodeWriter = new QRCodeWriter();
        Map<EncodeHintType, Object> hints = Map.of(EncodeHintType.MARGIN, String.valueOf(margin), EncodeHintType.ERROR_CORRECTION, level);
        // Encode at native module size first so ZXing does not pad a small QR into a large canvas (which
        // makes high-version tx QRs look tiny with a huge quiet zone). Then request an exact integer
        // multiple so the printed image fills `size` points.
        BitMatrix tight = qrCodeWriter.encode(data, BarcodeFormat.QR_CODE, 0, 0, hints);
        int scale = Math.max(4, (size * 3 + tight.getWidth() - 1) / tight.getWidth());
        int pixelSize = tight.getWidth() * scale;
        BitMatrix matrix = qrCodeWriter.encode(data, BarcodeFormat.QR_CODE, pixelSize, pixelSize, hints);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        MatrixToImageWriter.writeToStream(matrix, "PNG", outputStream, new MatrixToImageConfig());
        return outputStream.toByteArray();
    }

    static String formatAmount(long sats) {
        UnitFormat format = Config.get().getUnitFormat() == null ? UnitFormat.DOT : Config.get().getUnitFormat();
        return format.formatBtcValue(sats) + " BTC (" + format.formatSatsValue(sats) + " sats)";
    }
}
