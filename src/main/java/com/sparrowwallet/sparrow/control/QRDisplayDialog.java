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
import com.sparrowwallet.sparrow.io.Config;
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
import java.util.Locale;
import java.util.Optional;

@SuppressWarnings("deprecation")
public class QRDisplayDialog extends Dialog<UR> {
    private static final Logger log = LoggerFactory.getLogger(QRDisplayDialog.class);

    private static final int MIN_FRAGMENT_LENGTH = 10;

    private static final int ANIMATION_PERIOD_MILLIS = 200;

    private static final int QR_WIDTH = 480;
    private static final int QR_HEIGHT = 480;

    private final UR ur;
    private UREncoder encoder;

    private final ImageView qrImageView;

    private AnimateQRService animateQRService;
    private String currentPart;

    private boolean addLegacyEncodingOption;
    private boolean useLegacyEncoding;
    private String[] legacyParts;
    private int legacyPartIndex;

    private static boolean initialDensityChange;

    public QRDisplayDialog(String type, byte[] data, boolean addLegacyEncodingOption) throws UR.URException {
        this(UR.fromBytes(type, data), addLegacyEncodingOption);
    }

    public QRDisplayDialog(UR ur) {
        this(ur, false);
    }

    public QRDisplayDialog(UR ur, boolean addLegacyEncodingOption) {
        this.ur = ur;
        this.addLegacyEncodingOption = addLegacyEncodingOption;
        this.encoder = new UREncoder(ur, Config.get().getQrDensity().getMaxFragmentLength(), MIN_FRAGMENT_LENGTH, 0);

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
            createAnimateQRService();
        }

        final ButtonType cancelButtonType = new javafx.scene.control.ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialogPane.getButtonTypes().add(cancelButtonType);

        if(addLegacyEncodingOption) {
            final ButtonType legacyEncodingButtonType = new javafx.scene.control.ButtonType("Use Legacy Encoding (Cobo Vault)", ButtonBar.ButtonData.LEFT);
            dialogPane.getButtonTypes().add(legacyEncodingButtonType);
        } else {
            final ButtonType densityButtonType = new javafx.scene.control.ButtonType("Change Density", ButtonBar.ButtonData.LEFT);
            dialogPane.getButtonTypes().add(densityButtonType);
        }

        dialogPane.setPrefWidth(40 + QR_WIDTH + 40);
        dialogPane.setPrefHeight(40 + QR_HEIGHT + 85);
        AppServices.moveToActiveWindowScreen(this);

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
        dialogPane.setPrefWidth(40 + QR_WIDTH + 40);
        dialogPane.setPrefHeight(40 + QR_HEIGHT + 85);
        AppServices.moveToActiveWindowScreen(this);

        setResultConverter(dialogButton -> dialogButton != cancelButtonType ? ur : null);
    }

    private void createAnimateQRService() {
        animateQRService = new AnimateQRService();
        animateQRService.setPeriod(Duration.millis(ANIMATION_PERIOD_MILLIS));
        animateQRService.start();
        setOnCloseRequest(event -> {
            animateQRService.cancel();
        });
    }

    private void nextPart() {
        if(!useLegacyEncoding) {
            String fragment = encoder.nextPart();
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
            BitMatrix qrMatrix = qrCodeWriter.encode(fragment, BarcodeFormat.QR_CODE, QR_WIDTH, QR_HEIGHT);

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
                } else if(animateQRService == null) {
                    createAnimateQRService();
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
            } else if(animateQRService == null) {
                createAnimateQRService();
            } else if(!animateQRService.isRunning()) {
                animateQRService.reset();
                animateQRService.start();
            }
        }
    }

    private void changeQRDensity() {
        if(animateQRService != null) {
            animateQRService.cancel();
        }

        this.encoder = new UREncoder(ur, Config.get().getQrDensity().getMaxFragmentLength(), MIN_FRAGMENT_LENGTH, 0);
        nextPart();
        if(encoder.isSinglePart()) {
            qrImageView.setImage(getQrCode(currentPart));
        } else {
            createAnimateQRService();
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
                        if(!initialDensityChange && !encoder.isSinglePart()) {
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
