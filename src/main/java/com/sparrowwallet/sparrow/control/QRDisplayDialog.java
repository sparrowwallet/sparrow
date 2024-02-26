package com.sparrowwallet.sparrow.control;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.client.j2se.MatrixToImageConfig;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.sparrowwallet.hummingbird.LegacyUREncoder;
import com.sparrowwallet.hummingbird.registry.RegistryType;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.glyphfont.FontAwesome5;
import com.sparrowwallet.sparrow.io.Config;
import com.sparrowwallet.sparrow.io.ImportException;
import com.sparrowwallet.hummingbird.UR;
import com.sparrowwallet.hummingbird.UREncoder;
import com.sparrowwallet.sparrow.io.bbqr.BBQR;
import com.sparrowwallet.sparrow.io.bbqr.BBQREncoder;
import com.sparrowwallet.sparrow.io.bbqr.BBQREncoding;
import javafx.concurrent.ScheduledService;
import javafx.concurrent.Task;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;
import org.controlsfx.glyphfont.Glyph;
import org.controlsfx.tools.Borders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@SuppressWarnings("deprecation")
public class QRDisplayDialog extends Dialog<ButtonType> {
    private static final Logger log = LoggerFactory.getLogger(QRDisplayDialog.class);

    private static final int MIN_FRAGMENT_LENGTH = 10;

    private static final int ANIMATION_PERIOD_MILLIS = 200;

    private static final int DEFAULT_QR_SIZE = 580;
    private static final int REDUCED_QR_SIZE = 520;

    private static final BBQREncoding DEFAULT_BBQR_ENCODING = BBQREncoding.ZLIB;

    private final int qrSize = getQRSize();

    private final UR ur;
    private UREncoder urEncoder;

    private final BBQR bbqr;
    private BBQREncoder bbqrEncoder;
    private boolean useBbqrEncoding;

    private final ImageView qrImageView;

    private AnimateQRService animateQRService;
    private String currentPart;

    private boolean addLegacyEncodingOption;
    private boolean useLegacyEncoding;
    private String[] legacyParts;
    private int legacyPartIndex;

    private static boolean initialDensityChange;

    public QRDisplayDialog(String type, byte[] data, boolean addLegacyEncodingOption) throws UR.URException {
        this(UR.fromBytes(type, data), null, addLegacyEncodingOption, false, false);
    }

    public QRDisplayDialog(UR ur) {
        this(ur, null, false, false, false);
    }

    public QRDisplayDialog(UR ur, BBQR bbqr, boolean addLegacyEncodingOption, boolean addScanButton, boolean selectBbqrButton) {
        this.ur = ur;
        this.bbqr = bbqr;
        this.addLegacyEncodingOption = bbqr == null && addLegacyEncodingOption;

        this.urEncoder = new UREncoder(ur, Config.get().getQrDensity().getMaxUrFragmentLength(), MIN_FRAGMENT_LENGTH, 0);

        if(bbqr != null) {
            this.bbqrEncoder = new BBQREncoder(bbqr.type(), DEFAULT_BBQR_ENCODING, bbqr.data(), Config.get().getQrDensity().getMaxBbqrFragmentLength(), 0);
            if(selectBbqrButton) {
                useBbqrEncoding = true;
            }
        }

        final DialogPane dialogPane = new QRDisplayDialogPane();
        setDialogPane(dialogPane);
        AppServices.setStageIcon(dialogPane.getScene().getWindow());

        StackPane stackPane = new StackPane();
        qrImageView = new ImageView();
        stackPane.getChildren().add(qrImageView);

        dialogPane.setContent(Borders.wrap(stackPane).lineBorder().buildAll());

        nextPart();
        if(isSinglePart()) {
            qrImageView.setImage(getQrCode(currentPart));
        } else {
            createAnimateQRService();
        }

        final ButtonType cancelButtonType = new javafx.scene.control.ButtonType("Close", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialogPane.getButtonTypes().add(cancelButtonType);

        if(this.addLegacyEncodingOption) {
            final ButtonType legacyEncodingButtonType = new javafx.scene.control.ButtonType("Use Legacy Encoding (Cobo Vault)", ButtonBar.ButtonData.LEFT);
            dialogPane.getButtonTypes().add(legacyEncodingButtonType);
        } else {
            final ButtonType densityButtonType = new javafx.scene.control.ButtonType("Change Density", ButtonBar.ButtonData.LEFT);
            dialogPane.getButtonTypes().add(densityButtonType);
        }

        if(bbqr != null) {
            final ButtonType bbqrButtonType = new javafx.scene.control.ButtonType("Show BBQr", ButtonBar.ButtonData.BACK_PREVIOUS);
            dialogPane.getButtonTypes().add(bbqrButtonType);
        }

        if(addScanButton) {
            final ButtonType scanButtonType = new javafx.scene.control.ButtonType("Scan QR", ButtonBar.ButtonData.OK_DONE);
            dialogPane.getButtonTypes().add(scanButtonType);
        }

        dialogPane.setPrefWidth(40 + qrSize + 40);
        dialogPane.setPrefHeight(40 + qrSize + 85);
        dialogPane.setMinHeight(dialogPane.getPrefHeight());
        AppServices.moveToActiveWindowScreen(this);

        setResultConverter(dialogButton -> dialogButton);
    }

    public QRDisplayDialog(String data) {
        this(data, false);
    }

    public QRDisplayDialog(String data, boolean addScanButton) {
        this.ur = null;
        this.bbqr = null;
        this.urEncoder = null;
        this.bbqrEncoder = null;

        final DialogPane dialogPane = new QRDisplayDialogPane();
        setDialogPane(dialogPane);
        AppServices.setStageIcon(dialogPane.getScene().getWindow());

        StackPane stackPane = new StackPane();
        qrImageView = new ImageView();
        stackPane.getChildren().add(qrImageView);

        dialogPane.setContent(Borders.wrap(stackPane).lineBorder().buildAll());
        qrImageView.setImage(getQrCode(data));

        final ButtonType cancelButtonType = new javafx.scene.control.ButtonType("Close", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialogPane.getButtonTypes().addAll(cancelButtonType);

        if(addScanButton) {
            final ButtonType scanButtonType = new javafx.scene.control.ButtonType("Scan QR", ButtonBar.ButtonData.OK_DONE);
            dialogPane.getButtonTypes().add(scanButtonType);
        }

        dialogPane.setPrefWidth(40 + qrSize + 40);
        dialogPane.setPrefHeight(40 + qrSize + 85);
        dialogPane.setMinHeight(dialogPane.getPrefHeight());
        AppServices.moveToActiveWindowScreen(this);

        setResultConverter(dialogButton -> dialogButton);
    }

    private int getQRSize() {
        return AppServices.isReducedWindowHeight() ? REDUCED_QR_SIZE : DEFAULT_QR_SIZE;
    }

    private void createAnimateQRService() {
        animateQRService = new AnimateQRService();
        animateQRService.setPeriod(Duration.millis(ANIMATION_PERIOD_MILLIS));
        animateQRService.start();
        setOnCloseRequest(event -> {
            animateQRService.cancel();
        });
    }

    private boolean isSinglePart() {
        if(useBbqrEncoding) {
            return bbqrEncoder.isSinglePart();
        } else if(!useLegacyEncoding) {
            return urEncoder.isSinglePart();
        } else {
            return legacyParts.length == 1;
        }
    }

    private void nextPart() {
        if(useBbqrEncoding) {
            String fragment = bbqrEncoder.nextPart();
            currentPart = fragment.toUpperCase(Locale.ROOT);
        } else if(!useLegacyEncoding) {
            String fragment = urEncoder.nextPart();
            currentPart = fragment.toUpperCase(Locale.ROOT);
        } else {
            currentPart = legacyParts[legacyPartIndex];
            legacyPartIndex++;
            if(legacyPartIndex > legacyParts.length - 1) {
                legacyPartIndex = 0;
            }
        }
    }

    protected Image getQrCode(String fragment) {
        try {
            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            BitMatrix qrMatrix = qrCodeWriter.encode(fragment, BarcodeFormat.QR_CODE, qrSize, qrSize, Map.of(EncodeHintType.MARGIN, "2"));

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(qrMatrix, "PNG", baos, new MatrixToImageConfig());

            ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
            return new Image(bais);
        } catch(Exception e) {
            log.error("Error generating QR", e);
        }

        return null;
    }

    private void setUseLegacyEncoding(boolean useLegacyEncoding) {
        if(useLegacyEncoding) {
            try {
                //Force to be bytes type for legacy encoding
                LegacyUREncoder legacyEncoder = new LegacyUREncoder(new UR(RegistryType.BYTES.toString(), ur.getCborBytes()));
                this.legacyParts = legacyEncoder.encode();
                this.useLegacyEncoding = true;

                restartAnimation();
            } catch(UR.InvalidTypeException e) {
                //Can't happen
            }
        } else {
            this.useLegacyEncoding = false;
            restartAnimation();
        }
    }

    private void setUseBbqrEncoding(boolean useBbqrEncoding) {
        if(useBbqrEncoding) {
            this.useBbqrEncoding = true;
            restartAnimation();
        } else {
            this.useBbqrEncoding = false;
            restartAnimation();
        }
    }

    private void changeQRDensity() {
        if(animateQRService != null) {
            animateQRService.cancel();
        }

        if(bbqr != null) {
            this.bbqrEncoder = new BBQREncoder(bbqr.type(), DEFAULT_BBQR_ENCODING, bbqr.data(), Config.get().getQrDensity().getMaxBbqrFragmentLength(), 0);
        }

        this.urEncoder = new UREncoder(ur, Config.get().getQrDensity().getMaxUrFragmentLength(), MIN_FRAGMENT_LENGTH, 0);

        restartAnimation();
    }

    private void restartAnimation() {
        if(isSinglePart()) {
            if(animateQRService != null) {
                animateQRService.cancel();
            }

            nextPart();
            qrImageView.setImage(getQrCode(currentPart));
        } else if(animateQRService == null) {
            createAnimateQRService();
        } else if(!animateQRService.isRunning()) {
            animateQRService.reset();
            animateQRService.start();
        }
    }

    private class AnimateQRService extends ScheduledService<Boolean> {
        @Override
        protected Task<Boolean> createTask() {
            return new Task<>() {
                protected Boolean call() throws ImportException {
                    Image qrImage = getQrCode(currentPart);
                    qrImageView.setImage(qrImage);
                    nextPart();

                    return true;
                }
            };
        }
    }

    private class QRDisplayDialogPane extends DialogPane {
        @Override
        protected Node createButton(ButtonType buttonType) {
            if(buttonType.getButtonData() == ButtonBar.ButtonData.LEFT) {
                if(addLegacyEncodingOption) {
                    ToggleButton legacy = new ToggleButton(buttonType.getText());
                    legacy.setGraphicTextGap(5);
                    setLegacyGraphic(legacy, false);

                    final ButtonBar.ButtonData buttonData = buttonType.getButtonData();
                    ButtonBar.setButtonData(legacy, buttonData);
                    legacy.selectedProperty().addListener((observable, oldValue, newValue) -> {
                        setUseLegacyEncoding(newValue);
                        setLegacyGraphic(legacy, newValue);
                    });

                    return legacy;
                } else {
                    Button density = new Button(buttonType.getText());
                    density.setPrefWidth(160);
                    density.setGraphicTextGap(5);
                    updateDensityButton(density);

                    final ButtonBar.ButtonData buttonData = buttonType.getButtonData();
                    ButtonBar.setButtonData(density, buttonData);
                    density.setOnAction(event -> {
                        if(!initialDensityChange && !isSinglePart()) {
                            Optional<ButtonType> optButtonType = AppServices.showWarningDialog("Discard progress?", "Changing the QR code density means any progress on the receiving device must be discarded. Proceed?", ButtonType.NO, ButtonType.YES);
                            if(optButtonType.isPresent() && optButtonType.get() == ButtonType.YES) {
                                initialDensityChange = true;
                            } else {
                                return;
                            }
                        }

                        Config.get().setQrDensity(Config.get().getQrDensity() == QRDensity.NORMAL ? QRDensity.LOW : QRDensity.NORMAL);
                        updateDensityButton(density);
                        changeQRDensity();
                    });

                    return density;
                }
            } else if(buttonType.getButtonData() == ButtonBar.ButtonData.OK_DONE) {
                Button scanButton = (Button)super.createButton(buttonType);
                scanButton.setGraphicTextGap(5);
                scanButton.setGraphic(getGlyph(FontAwesome5.Glyph.CAMERA));

                return scanButton;
            } else if(buttonType.getButtonData() == ButtonBar.ButtonData.BACK_PREVIOUS) {
                ToggleButton bbqr = new ToggleButton(buttonType.getText());
                bbqr.setGraphicTextGap(5);
                bbqr.setGraphic(getGlyph(FontAwesome5.Glyph.QRCODE));
                bbqr.setSelected(useBbqrEncoding);
                final ButtonBar.ButtonData buttonData = buttonType.getButtonData();
                ButtonBar.setButtonData(bbqr, buttonData);

                bbqr.selectedProperty().addListener((observable, oldValue, newValue) -> {
                    setUseBbqrEncoding(newValue);
                });

                return bbqr;
            }

            return super.createButton(buttonType);
        }

        private void setLegacyGraphic(ToggleButton legacy, boolean useLegacyEncoding) {
            if(useLegacyEncoding) {
                legacy.setGraphic(getGlyph(FontAwesome5.Glyph.CHECK_CIRCLE));
            } else {
                legacy.setGraphic(getGlyph(FontAwesome5.Glyph.BAN));
            }
        }

        private void updateDensityButton(Button density) {
            density.setText(Config.get().getQrDensity() == QRDensity.NORMAL ? "Decrease Density" : "Increase Density");
            if(Config.get().getQrDensity() == QRDensity.NORMAL) {
                density.setGraphic(getGlyph(FontAwesome5.Glyph.MAGNIFYING_GLASS_PLUS));
            } else {
                density.setGraphic(getGlyph(FontAwesome5.Glyph.MAGNIFYING_GLASS_MINUS));
            }
        }
    }

    protected static Glyph getGlyph(FontAwesome5.Glyph glyphName) {
        Glyph glyph = new Glyph(FontAwesome5.FONT_NAME, glyphName);
        glyph.setFontSize(11);
        return glyph;
    }
}
