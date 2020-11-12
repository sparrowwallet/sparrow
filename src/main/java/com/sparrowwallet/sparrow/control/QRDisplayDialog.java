package com.sparrowwallet.sparrow.control;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageConfig;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.sparrowwallet.sparrow.AppController;
import com.sparrowwallet.sparrow.io.ImportException;
import com.sparrowwallet.hummingbird.UR;
import com.sparrowwallet.hummingbird.UREncoder;
import javafx.concurrent.ScheduledService;
import javafx.concurrent.Task;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;
import org.controlsfx.tools.Borders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

public class QRDisplayDialog extends Dialog<UR> {
    private static final Logger log = LoggerFactory.getLogger(QRDisplayDialog.class);

    private static final int MIN_FRAGMENT_LENGTH = 10;
    private static final int MAX_FRAGMENT_LENGTH = 100;

    private static final int ANIMATION_PERIOD_MILLIS = 200;

    private final UR ur;
    private final UREncoder encoder;

    private final ImageView qrImageView;

    private String currentPart;

    public QRDisplayDialog(String type, byte[] data) throws UR.URException {
        this(UR.fromBytes(type, data));
    }

    public QRDisplayDialog(UR ur) {
        this.ur = ur;
        this.encoder = new UREncoder(ur, MAX_FRAGMENT_LENGTH, MIN_FRAGMENT_LENGTH, 0);

        final DialogPane dialogPane = getDialogPane();
        AppController.setStageIcon(dialogPane.getScene().getWindow());

        StackPane stackPane = new StackPane();
        qrImageView = new ImageView();
        stackPane.getChildren().add(qrImageView);

        dialogPane.setContent(Borders.wrap(stackPane).lineBorder().buildAll());

        nextPart();
        if(encoder.isSinglePart()) {
            qrImageView.setImage(getQrCode(currentPart));
        } else {
            AnimateQRService animateQRService = new AnimateQRService();
            animateQRService.setPeriod(Duration.millis(ANIMATION_PERIOD_MILLIS));
            animateQRService.start();
            setOnCloseRequest(event -> {
                animateQRService.cancel();
            });
        }

        final ButtonType cancelButtonType = new javafx.scene.control.ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialogPane.getButtonTypes().addAll(cancelButtonType);
        dialogPane.setPrefWidth(500);
        dialogPane.setPrefHeight(550);

        setResultConverter(dialogButton -> dialogButton != cancelButtonType ? ur : null);
    }

    public QRDisplayDialog(String data) {
        this.ur = null;
        this.encoder = null;

        final DialogPane dialogPane = getDialogPane();
        AppController.setStageIcon(dialogPane.getScene().getWindow());

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
        String fragment = encoder.nextPart();
        currentPart = fragment.toUpperCase();
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
}
