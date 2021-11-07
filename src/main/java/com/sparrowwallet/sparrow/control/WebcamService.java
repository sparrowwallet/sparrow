package com.sparrowwallet.sparrow.control;

import com.github.sarxos.webcam.*;
import com.google.zxing.*;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import com.sparrowwallet.sparrow.io.Config;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.concurrent.ScheduledService;
import javafx.concurrent.Task;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;

import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class WebcamService extends ScheduledService<Image> {
    private WebcamResolution resolution;
    private WebcamDevice device;
    private final WebcamListener listener;
    private final WebcamUpdater.DelayCalculator delayCalculator;
    private final BooleanProperty opening = new SimpleBooleanProperty(false);

    private final ObjectProperty<Result> resultProperty = new SimpleObjectProperty<>(null);

    private static final int QR_SAMPLE_PERIOD_MILLIS = 200;

    private Webcam cam;
    private long lastQrSampleTime;
    private final Reader qrReader;

    static {
        Webcam.setDriver(new WebcamScanDriver());
    }

    public WebcamService(WebcamResolution resolution, WebcamDevice device, WebcamListener listener, WebcamUpdater.DelayCalculator delayCalculator) {
        this.resolution = resolution;
        this.device = device;
        this.listener = listener;
        this.delayCalculator = delayCalculator;
        this.lastQrSampleTime = System.currentTimeMillis();
        this.qrReader = new QRCodeReader();
    }

    @Override
    public Task<Image> createTask() {
        return new Task<Image>() {
            @Override
            protected Image call() throws Exception {
                try {
                    if(cam == null) {
                        List<Webcam> webcams = Webcam.getWebcams(1, TimeUnit.MINUTES);
                        if(webcams.isEmpty()) {
                            throw new UnsupportedOperationException("No camera available.");
                        }

                        cam = webcams.get(0);

                        if(device != null) {
                            for(Webcam webcam : webcams) {
                                if(webcam.getDevice().getName().equals(device.getName())) {
                                    cam = webcam;
                                }
                            }
                        } else if(Config.get().getWebcamDevice() != null) {
                            for(Webcam webcam : webcams) {
                                if(webcam.getDevice().getName().equals(Config.get().getWebcamDevice())) {
                                    cam = webcam;
                                }
                            }
                        }

                        device = cam.getDevice();

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
                    if(bimg == null) {
                        return null;
                    }
                    BufferedImage croppedImage = getCroppedImage(bimg);

                    Image image = SwingFXUtils.toFXImage(bimg, null);
                    updateValue(image);

                    if(System.currentTimeMillis() > (lastQrSampleTime + QR_SAMPLE_PERIOD_MILLIS)) {
                        readQR(croppedImage);
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
            Result result = qrReader.decode(bitmap, Map.of(DecodeHintType.TRY_HARDER, Boolean.TRUE));
            resultProperty.set(result);
        } catch(ReaderException e) {
            // fall thru, it means there is no QR code in image
        }
    }

    private BufferedImage getCroppedImage(BufferedImage bufferedImage) {
        int dimension = Math.min(bufferedImage.getWidth(), bufferedImage.getHeight());
        int squareSize = dimension / 2;
        int x = (bufferedImage.getWidth() - squareSize) / 2;
        int y = (bufferedImage.getHeight() - squareSize) / 2;

        Graphics2D g2d = (Graphics2D)bufferedImage.getGraphics();
        float[] dash1 = {10.0f};
        g2d.setColor(Color.BLACK);
        g2d.setStroke(new BasicStroke(3.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10.0f, dash1, 0.0f));
        g2d.draw(new RoundRectangle2D.Double(x, y, squareSize, squareSize, 10, 10));
        g2d.dispose();

        return bufferedImage.getSubimage(x, y, squareSize, squareSize);
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

    public WebcamDevice getDevice() {
        return device;
    }

    public void setDevice(WebcamDevice device) {
        this.device = device;
    }

    public boolean isOpening() {
        return opening.get();
    }

    public BooleanProperty openingProperty() {
        return opening;
    }
}
