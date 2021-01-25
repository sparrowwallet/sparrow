package com.sparrowwallet.sparrow.control;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageConfig;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.sparrowwallet.hummingbird.LegacyUREncoder;
import com.sparrowwallet.hummingbird.registry.RegistryType;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.glyphfont.FontAwesome5;
import com.sparrowwallet.sparrow.io.ImportException;
import com.sparrowwallet.hummingbird.UR;
import com.sparrowwallet.hummingbird.UREncoder;
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

@SuppressWarnings("deprecation")
public class QRDisplayDialog extends Dialog<UR> {
    private static final Logger log = LoggerFactory.getLogger(QRDisplayDialog.class);

    private static final int MIN_FRAGMENT_LENGTH = 10;
    private static final int MAX_FRAGMENT_LENGTH = 100;

    private static final int ANIMATION_PERIOD_MILLIS = 400;

    private final UR ur;
    private final UREncoder encoder;

    private final ImageView qrImageView;

    private AnimateQRService animateQRService;
    private String currentPart;

    private boolean useLegacyEncoding;
    private String[] legacyParts;
    private int legacyPartIndex;

    public QRDisplayDialog(String type, byte[] data, boolean addLegacyEncodingOption) throws UR.URException {
        this(UR.fromBytes(type, data), addLegacyEncodingOption);
    }

    public QRDisplayDialog(UR ur) {
        this(ur, false);
    }

    public QRDisplayDialog(UR ur, boolean addLegacyEncodingOption) {
        this.ur = ur;
        this.encoder = new UREncoder(ur, MAX_FRAGMENT_LENGTH, MIN_FRAGMENT_LENGTH, 0);

        final DialogPane dialogPane = new QRDisplayDialogPane();
        setDialogPane(dialogPane);
        AppServices.setStageIcon(dialogPane.getScene().getWindow());

        StackPane stackPane = new StackPane();
        qrImageView = new ImageView();
        stackPane.getChildren().add(qrImageView);

        dialogPane.setContent(Borders.wrap(stackPane).lineBorder().buildAll());

        nextPart();
        if(encoder.isSinglePart()) {
            qrImageView.setImage(getQrCode(currentPart));
        } else {
            animateQRService = new AnimateQRService();
            animateQRService.setPeriod(Duration.millis(ANIMATION_PERIOD_MILLIS));
            animateQRService.start();
            setOnCloseRequest(event -> {
                animateQRService.cancel();
            });
        }

        final ButtonType cancelButtonType = new javafx.scene.control.ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialogPane.getButtonTypes().add(cancelButtonType);

        if(addLegacyEncodingOption) {
            final ButtonType legacyEncodingButtonType = new javafx.scene.control.ButtonType("Use Legacy Encoding (Cobo Vault)", ButtonBar.ButtonData.LEFT);
            dialogPane.getButtonTypes().add(legacyEncodingButtonType);
        }

        dialogPane.setPrefWidth(500);
        dialogPane.setPrefHeight(550);

        setResultConverter(dialogButton -> dialogButton != cancelButtonType ? ur : null);
    }

    public QRDisplayDialog(String data) {
        this.ur = null;
        this.encoder = null;

        final DialogPane dialogPane = getDialogPane();
        AppServices.setStageIcon(dialogPane.getScene().getWindow());

        StackPane stackPane = new StackPane();
        qrImageView = new ImageView();
        stackPane.getChildren().add(qrImageView);

        dialogPane.setContent(Borders.wrap(stackPane).lineBorder().buildAll());
        qrImageView.setImage(getQrCode(data));

        final ButtonType cancelButtonType = new javafx.scene.control.ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialogPane.getButtonTypes().addAll(cancelButtonType);
        dialogPane.setPrefWidth(500);
        dialogPane.setPrefHeight(550);

        setResultConverter(dialogButton -> dialogButton != cancelButtonType ? ur : null);
    }

    private void nextPart() {
        if(!useLegacyEncoding) {
            String fragment = encoder.nextPart();
            currentPart = fragment.toUpperCase();
        } else {
            currentPart = legacyParts[legacyPartIndex];
            legacyPartIndex++;
            if(legacyPartIndex > legacyParts.length - 1) {
                legacyPartIndex = 0;
            }
        }
    }

    private Image getQrCode(String fragment) {
        try {
            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            BitMatrix qrMatrix = qrCodeWriter.encode(fragment, BarcodeFormat.QR_CODE, 480, 480);

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

                if(legacyParts.length == 1) {
                    if(animateQRService != null) {
                        animateQRService.cancel();
                    }

                    nextPart();
                    qrImageView.setImage(getQrCode(currentPart));
                } else if(!animateQRService.isRunning()) {
                    animateQRService.reset();
                    animateQRService.start();
                }
            } catch(UR.InvalidTypeException e) {
                //Can't happen
            }
        } else {
            this.useLegacyEncoding = false;

            if(encoder.isSinglePart()) {
                if(animateQRService != null) {
                    animateQRService.cancel();
                }

                qrImageView.setImage(getQrCode(currentPart));
            } else if(!animateQRService.isRunning()) {
                animateQRService.reset();
                animateQRService.start();
            }
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
            }

            return super.createButton(buttonType);
        }

        private void setLegacyGraphic(ToggleButton legacy, boolean useLegacyEncoding) {
            if(useLegacyEncoding) {
                legacy.setGraphic(getGlyph(FontAwesome5.Glyph.CHECK_CIRCLE));
            } else {
                legacy.setGraphic(getGlyph(FontAwesome5.Glyph.QUESTION_CIRCLE));
            }
        }

        private Glyph getGlyph(FontAwesome5.Glyph glyphName) {
            Glyph glyph = new Glyph(FontAwesome5.FONT_NAME, glyphName);
            glyph.setFontSize(11);
            return glyph;
        }
    }
}
