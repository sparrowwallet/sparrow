package com.sparrowwallet.sparrow.control;

import com.github.sarxos.webcam.Webcam;
import com.github.sarxos.webcam.WebcamListener;
import com.github.sarxos.webcam.WebcamResolution;
import com.github.sarxos.webcam.WebcamUpdater;
import com.google.zxing.*;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.concurrent.ScheduledService;
import javafx.concurrent.Task;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;

import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

public class WebcamService extends ScheduledService<Image> {
    private WebcamResolution resolution;
    private final WebcamListener listener;
    private final WebcamUpdater.DelayCalculator delayCalculator;
    private final BooleanProperty opening = new SimpleBooleanProperty(false);

    private final ObjectProperty<Result> resultProperty = new SimpleObjectProperty<>(null);

    private static final int QR_SAMPLE_PERIOD_MILLIS = 400;

    private Webcam cam;
    private long lastQrSampleTime;

    public WebcamService(WebcamResolution resolution, WebcamListener listener, WebcamUpdater.DelayCalculator delayCalculator) {
        this.resolution = resolution;
        this.listener = listener;
        this.delayCalculator = delayCalculator;
        this.lastQrSampleTime = System.currentTimeMillis();
    }

    @Override
    public Task<Image> createTask() {
        return new Task<Image>() {
            @Override
            protected Image call() throws Exception {
                try {
                    if(cam == null) {
                        cam = Webcam.getWebcams(1, TimeUnit.MINUTES).get(0);
                        cam.setCustomViewSizes(resolution.getSize());
                        cam.setViewSize(resolution.getSize());
                        if(!Arrays.asList(cam.getWebcamListeners()).contains(listener)) {
                            cam.addWebcamListener(listener);
                        }

                        opening.set(true);
                        cam.open(true, delayCalculator);
                        opening.set(false);
                    }

                    BufferedImage bimg = cam.getImage();
                    Image image = SwingFXUtils.toFXImage(bimg, null);
                    updateValue(image);

                    if(System.currentTimeMillis() > (lastQrSampleTime + QR_SAMPLE_PERIOD_MILLIS)) {
                        readQR(bimg);
                        lastQrSampleTime = System.currentTimeMillis();
                    }

                    return image;
                } finally {
                    opening.set(false);
                }
            }
        };
    }

    @Override
    public void reset() {
        cam = null;
        super.reset();
    }

    @Override
    public boolean cancel() {
        if(cam != null && !cam.close()) {
            cam.close();
        }

        return super.cancel();
    }

    private void readQR(BufferedImage bufferedImage) {
        LuminanceSource source = new BufferedImageLuminanceSource(bufferedImage);
        BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));

        try {
            Result result = new MultiFormatReader().decode(bitmap);
            resultProperty.set(result);
        } catch(NotFoundException e) {
            // fall thru, it means there is no QR code in image
        }
    }

    public Result getResult() {
        return resultProperty.get();
    }

    public ObjectProperty<Result> resultProperty() {
        return resultProperty;
    }

    public int getCamWidth() {
        return resolution.getSize().width;
    }

    public int getCamHeight() {
        return resolution.getSize().height;
    }

    public void setResolution(WebcamResolution resolution) {
        this.resolution = resolution;
    }

    public boolean isOpening() {
        return opening.get();
    }

    public BooleanProperty openingProperty() {
        return opening;
    }
}
