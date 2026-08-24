package com.sparrowwallet.sparrow.io;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import com.sparrowwallet.drongo.Network;
import com.sparrowwallet.drongo.policy.PolicyType;
import com.sparrowwallet.drongo.protocol.ScriptType;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.SparrowWallet;
import com.sparrowwallet.sparrow.timelockrecovery.TimelockRecovery;
import com.sparrowwallet.sparrow.timelockrecovery.TimelockRecoveryFixtures;
import com.sparrowwallet.sparrow.timelockrecovery.TimelockRecoveryPlan;
import com.sparrowwallet.sparrow.timelockrecovery.TimelockRecoveryRecipient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;

public class TimelockRecoveryPdfTest {
    private static final Instant CREATED_AT = Instant.parse("2026-08-24T12:00:00Z");
    private static final Pattern PAGE_NUMBER = Pattern.compile("Page:\\s*(\\d+)");
    private static final Pattern HEX_LINE = Pattern.compile("^[0-9A-F]+$");

    @TempDir
    private static Path tempHome;

    @BeforeAll
    public static void setup() {
        System.setProperty(SparrowWallet.APP_HOME_PROPERTY, tempHome.toString());
        Network.set(Network.MAINNET);
    }

    @AfterAll
    public static void tearDown() {
        System.clearProperty(SparrowWallet.APP_HOME_PROPERTY);
        Network.set(Network.MAINNET);
    }

    @Test
    public void wrapHexSplitsOnCourierWidth() {
        Assertions.assertEquals("", TimelockRecoveryPdf.wrapHex(""));
        Assertions.assertEquals("ABCD", TimelockRecoveryPdf.wrapHex("ABCD"));
        String wrapped = TimelockRecoveryPdf.wrapHex("A".repeat(2100));
        String[] lines = wrapped.split("\n", -1);
        for(String line : lines) {
            Assertions.assertTrue(line.length() <= TimelockRecoveryPdf.HEX_CHARS_PER_LINE, line.length() + " chars");
        }
        Assertions.assertEquals("A".repeat(2100), wrapped.replace("\n", ""));
        Assertions.assertEquals(22, lines.length);

        Font font = FontFactory.getFont(FontFactory.COURIER, 8);
        float lineWidth = font.getCalculatedBaseFont(false).getWidthPoint("0", 8) * TimelockRecoveryPdf.HEX_CHARS_PER_LINE;
        float contentWidth = PageSize.A4.getWidth() - 48f - 48f;
        Assertions.assertTrue(lineWidth <= contentWidth,
                "hex line width " + lineWidth + " exceeds content width " + contentWidth);
    }

    @Test
    public void formatAmountUsesBtcAndSats() {
        String formatted = TimelockRecoveryPdf.formatAmount(1_234_567L);
        Assertions.assertTrue(formatted.contains("BTC"));
        Assertions.assertTrue(formatted.contains("sats"));
        Assertions.assertTrue(formatted.contains("1234567") || formatted.contains("1,234,567") || formatted.contains("0.01234567"));
    }

    @Test
    public void txQrEncodesExactlyTheHexOnThatPage() throws Exception {
        String chunk = "A".repeat(TimelockRecovery.QR_HEX_CHUNK_SIZE);
        String remainder = "B".repeat(200);
        Assertions.assertEquals(chunk, decodeQr(TimelockRecoveryPdf.qrPng(chunk, ErrorCorrectionLevel.Q, 280, TimelockRecoveryPdf.TX_QR_MARGIN)));
        Assertions.assertEquals(remainder, decodeQr(TimelockRecoveryPdf.qrPng(remainder, ErrorCorrectionLevel.Q, 280, TimelockRecoveryPdf.TX_QR_MARGIN)));
        Assertions.assertTrue(quietZoneRatio(chunk) < 0.05, "large payload QR should not be padded with a huge quiet zone");
        Assertions.assertTrue(quietZoneRatio(remainder) < 0.05, "small payload QR should not be padded with a huge quiet zone");
    }

    @Test
    public void writesSmallGuidesWithHeadersAndHex() throws Exception {
        Wallet wallet = TimelockRecoveryFixtures.fundedWallet(ScriptType.P2WPKH, PolicyType.SINGLE_HD);
        TimelockRecovery recovery = TimelockRecoveryFixtures.signedRecovery(wallet,
                List.of(TimelockRecoveryRecipient.remaining(TimelockRecoveryFixtures.destination(), "Backup")),
                90, 2.0, true);
        Path dir = pdfOutputDir();
        File recoveryPdf = dir.resolve("recovery-guide-small.pdf").toFile();
        File cancellationPdf = dir.resolve("cancellation-guide-small.pdf").toFile();
        Instant createdAt = CREATED_AT;
        TimelockRecoveryPlan plan = TimelockRecoveryPlan.from(recovery, createdAt, "small-plan");
        TimelockRecoveryPdf.writeRecoveryGuide(recoveryPdf, recovery, plan, createdAt);
        TimelockRecoveryPdf.writeCancellationGuide(cancellationPdf, recovery, createdAt, "small-plan");

        assertValidGuide(recoveryPdf, "Recovery-Guide", recovery,
                TimelockRecovery.toUpperHex(recovery.getSignedInitiationTx()),
                TimelockRecovery.toUpperHex(recovery.getSignedRecoveryTx()), null);
        assertValidGuide(cancellationPdf, "Cancellation-Guide", recovery, null, null,
                TimelockRecovery.toUpperHex(recovery.getSignedCancellationTx()));
        writeIndex(dir);
    }

    @Test
    public void writesLargeGuidesSplittingHexAcrossPages() throws Exception {
        Wallet wallet = TimelockRecoveryFixtures.fundedWallet(ScriptType.P2WPKH, PolicyType.SINGLE_HD, 30, 200_000L);
        List<TimelockRecoveryRecipient> recipients = TimelockRecoveryFixtures.manyDestinations(40);
        TimelockRecovery recovery = TimelockRecoveryFixtures.signedRecovery(wallet, recipients, 90, 2.0, true);

        String initiationHex = TimelockRecovery.toUpperHex(recovery.getSignedInitiationTx());
        String recoveryHex = TimelockRecovery.toUpperHex(recovery.getSignedRecoveryTx());
        List<String> initiationParts = TimelockRecovery.splitHexForQr(initiationHex);
        List<String> recoveryParts = TimelockRecovery.splitHexForQr(recoveryHex);
        Assertions.assertTrue(initiationHex.length() >= TimelockRecovery.QR_HEX_SPLIT_THRESHOLD,
                "expected a large initiation tx, hex length " + initiationHex.length());
        Assertions.assertTrue(initiationParts.size() > 1, "Initiation hex should split, length " + initiationHex.length());
        Assertions.assertTrue(recoveryParts.size() > 1, "Recovery hex should split, length " + recoveryHex.length());

        Path dir = pdfOutputDir();
        File recoveryPdf = dir.resolve("recovery-guide-large.pdf").toFile();
        File cancellationPdf = dir.resolve("cancellation-guide-large.pdf").toFile();
        TimelockRecoveryPlan plan = TimelockRecoveryPlan.from(recovery, CREATED_AT, "large-plan");
        TimelockRecoveryPdf.writeRecoveryGuide(recoveryPdf, recovery, plan, CREATED_AT);
        TimelockRecoveryPdf.writeCancellationGuide(cancellationPdf, recovery, CREATED_AT, "large-plan");

        List<String> recoveryPages = pageTexts(recoveryPdf);
        Assertions.assertTrue(recoveryPages.size() >= 2 + initiationParts.size() + recoveryParts.size(),
                "recovery guide pages " + recoveryPages.size());
        assertConsecutivePageNumbers(recoveryPages);
        assertNoOverflowingLines(recoveryPages);
        assertHexDoesNotOverflowToNewPage(recoveryPages);
        Assertions.assertTrue(recoveryPages.get(0).contains("Timelock-Recovery Guide"));
        Assertions.assertTrue(joinedText(recoveryPages).contains("Part 1 of " + initiationParts.size()));
        Assertions.assertTrue(joinedText(recoveryPages).contains("Part 1 of " + recoveryParts.size()));
        Assertions.assertEquals(initiationHex, extractSectionHex(recoveryPages, "Initiation Transaction",
                recovery.getSignedInitiationTx().getTxId().toString()));
        Assertions.assertEquals(recoveryHex, extractSectionHex(recoveryPages, "Recovery Transaction",
                recovery.getSignedRecoveryTx().getTxId().toString()));
        assertPartPages(recoveryPages, "Initiation Transaction", initiationParts.size());
        assertPartPages(recoveryPages, "Recovery Transaction", recoveryParts.size());

        List<String> cancellationPages = pageTexts(cancellationPdf);
        Assertions.assertTrue(cancellationPages.size() >= 2, "cancellation guide pages " + cancellationPages.size());
        assertConsecutivePageNumbers(cancellationPages);
        assertNoOverflowingLines(cancellationPages);
        assertHexDoesNotOverflowToNewPage(cancellationPages);
        Assertions.assertTrue(cancellationPages.get(0).contains("Cancellation Guide"));
        Assertions.assertEquals(TimelockRecovery.toUpperHex(recovery.getSignedCancellationTx()),
                extractSectionHex(cancellationPages, "Cancellation Transaction",
                        recovery.getSignedCancellationTx().getTxId().toString()));
        writeIndex(dir);
        System.out.println("Timelock Recovery PDF fixtures: " + dir.toAbsolutePath());
    }

    private static void assertValidGuide(File file, String kind, TimelockRecovery recovery,
                                         String initiationHex, String recoveryHex, String cancellationHex) throws Exception {
        Assertions.assertTrue(file.isFile());
        Assertions.assertTrue(file.length() > 1000);
        List<String> pages = pageTexts(file);
        Assertions.assertFalse(pages.isEmpty());
        assertConsecutivePageNumbers(pages);
        assertNoOverflowingLines(pages);
        assertHexDoesNotOverflowToNewPage(pages);
        String all = joinedText(pages);
        Assertions.assertTrue(all.contains(kind));
        Assertions.assertTrue(all.contains(recovery.getWallet().getFullDisplayName()));
        if(initiationHex != null) {
            Assertions.assertEquals(initiationHex, extractSectionHex(pages, "Initiation Transaction",
                    recovery.getSignedInitiationTx().getTxId().toString()));
        }
        if(recoveryHex != null) {
            Assertions.assertEquals(recoveryHex, extractSectionHex(pages, "Recovery Transaction",
                    recovery.getSignedRecoveryTx().getTxId().toString()));
        }
        if(cancellationHex != null) {
            Assertions.assertEquals(cancellationHex, extractSectionHex(pages, "Cancellation Transaction",
                    recovery.getSignedCancellationTx().getTxId().toString()));
        }
    }

    private static void assertConsecutivePageNumbers(List<String> pages) {
        for(int i = 0; i < pages.size(); i++) {
            Matcher matcher = PAGE_NUMBER.matcher(pages.get(i));
            Assertions.assertTrue(matcher.find(), "missing page number on PDF page " + (i + 1) + ": " + pages.get(i));
            Assertions.assertEquals(String.valueOf(i + 1), matcher.group(1),
                    "wrong page number on physical page " + (i + 1));
        }
    }

    private static void assertNoOverflowingLines(List<String> pages) {
        for(int page = 0; page < pages.size(); page++) {
            for(String line : pages.get(page).split("\\R")) {
                String trimmed = line.trim();
                if(HEX_LINE.matcher(trimmed).matches() && trimmed.length() > 8) {
                    Assertions.assertTrue(trimmed.length() <= TimelockRecoveryPdf.HEX_CHARS_PER_LINE,
                            "page " + (page + 1) + " hex overflows at " + trimmed.length() + " chars");
                }
                Assertions.assertTrue(trimmed.length() < 160, "page " + (page + 1) + " line is too long: " + trimmed.length());
            }
        }
    }

    private static void assertHexDoesNotOverflowToNewPage(List<String> pages) {
        for(int page = 0; page < pages.size(); page++) {
            String text = pages.get(page);
            boolean hasTitle = text.contains("Initiation Transaction") || text.contains("Recovery Transaction")
                    || text.contains("Cancellation Transaction");
            boolean hasTxHex = extractUpperHexRuns(text).length() >= 40;
            Assertions.assertFalse(hasTxHex && !hasTitle,
                    "page " + (page + 1) + " has transaction hex without a title (hex overflowed to a new page)");
        }
    }

    private static void assertPartPages(List<String> pages, String title, int expectedParts) {
        int found = 0;
        for(String page : pages) {
            if(page.contains(title) && page.contains("Part ")) {
                found++;
            }
        }
        Assertions.assertEquals(expectedParts, found, title + " part pages");
    }

    private static String extractSectionHex(List<String> pages, String title, String txid) {
        StringBuilder hex = new StringBuilder();
        boolean inSection = false;
        for(String page : pages) {
            if(page.contains(title)) {
                inSection = true;
            } else if(inSection && isDifferentSection(page, title)) {
                break;
            }
            if(inSection) {
                hex.append(extractUpperHexRuns(page, txid));
            }
        }
        return hex.toString();
    }

    private static boolean isDifferentSection(String page, String currentTitle) {
        for(String title : List.of("Initiation Transaction", "Recovery Transaction", "Cancellation Transaction",
                "Step 2 - Waiting", "Step 3 - Broadcasting")) {
            if(!title.equals(currentTitle) && page.contains(title)) {
                return true;
            }
        }
        return false;
    }

    private static String extractUpperHexRuns(String pageText, String... exclude) {
        Set<String> skip = new HashSet<>();
        for(String value : exclude) {
            if(value != null) {
                skip.add(value.toUpperCase(Locale.ROOT));
            }
        }
        StringBuilder hex = new StringBuilder();
        for(String line : pageText.split("\\R")) {
            String trimmed = line.trim();
            if(HEX_LINE.matcher(trimmed).matches() && trimmed.length() >= 1 && !skip.contains(trimmed)) {
                hex.append(trimmed);
            }
        }
        return hex.toString();
    }

    private static String joinedText(List<String> pages) {
        return String.join("\n", pages);
    }

    private static List<String> pageTexts(File file) throws Exception {
        PdfReader reader = new PdfReader(file.getAbsolutePath());
        try {
            PdfTextExtractor extractor = new PdfTextExtractor(reader);
            List<String> pages = new ArrayList<>();
            for(int page = 1; page <= reader.getNumberOfPages(); page++) {
                pages.add(extractor.getTextFromPage(page));
            }
            return pages;
        } finally {
            reader.close();
        }
    }

    private static String decodeQr(byte[] png) throws Exception {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
        BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(image)));
        return new QRCodeReader().decode(bitmap, Map.of(DecodeHintType.TRY_HARDER, Boolean.TRUE)).getText();
    }

    private static float quietZoneRatio(String payload) throws Exception {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(
                TimelockRecoveryPdf.qrPng(payload, ErrorCorrectionLevel.Q, 280, TimelockRecoveryPdf.TX_QR_MARGIN)));
        int left = 0;
        while(left < image.getWidth() && columnIsWhite(image, left)) {
            left++;
        }
        return left / (float)image.getWidth();
    }

    private static boolean columnIsWhite(BufferedImage image, int x) {
        for(int y = 0; y < image.getHeight(); y++) {
            if((image.getRGB(x, y) & 0x00FFFFFF) < 0xF0F0F0) {
                return false;
            }
        }
        return true;
    }

    static Path pdfOutputDir() throws Exception {
        Path dir = Path.of("build", "tmp", "timelock-recovery-pdfs").toAbsolutePath();
        Files.createDirectories(dir);
        return dir;
    }

    private static void writeIndex(Path dir) throws Exception {
        String body = """
                Timelock Recovery PDF test output
                Generated under: %s

                recovery-guide-small.pdf       1-input / 1-destination Recovery Guide
                cancellation-guide-small.pdf   matching Cancellation Guide
                recovery-guide-large.pdf       30-input / 40-destination Recovery Guide (hex split across pages)
                cancellation-guide-large.pdf   Cancellation Guide for that large plan

                Open these files after ./gradlew :test --tests com.sparrowwallet.sparrow.io.TimelockRecoveryPdfTest
                """.formatted(dir);
        Files.writeString(dir.resolve("README.txt"), body, StandardCharsets.UTF_8);
    }
}
