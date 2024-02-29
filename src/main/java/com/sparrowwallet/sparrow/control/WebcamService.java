package com.sparrowwallet.sparrow.control;

import com.github.sarxos.webcam.*;
import com.google.zxing.*;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import com.sparrowwallet.bokmakierie.Bokmakierie;
import com.sparrowwallet.sparrow.io.Config;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.concurrent.ScheduledService;
import javafx.concurrent.Task;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;
import net.sourceforge.zbar.ZBar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class WebcamService extends ScheduledService<Image> {
    private static final Logger log = LoggerFactory.getLogger(WebcamService.class);

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
    private final Bokmakierie bokmakierie;

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
        this.bokmakierie = new Bokmakierie();
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

                    BufferedImage originalImage = cam.getImage();
                    if(originalImage == null) {
                        return null;
                    }

                    CroppedDimension cropped = getCroppedDimension(originalImage);
                    BufferedImage croppedImage = originalImage.getSubimage(cropped.x, cropped.y, cropped.length, cropped.length);
                    BufferedImage framedImage = getFramedImage(originalImage, cropped);

                    Image image = SwingFXUtils.toFXImage(framedImage, null);
                    updateValue(image);

                    if(System.currentTimeMillis() > (lastQrSampleTime + QR_SAMPLE_PERIOD_MILLIS)) {
                        readQR(originalImage, croppedImage);
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

    private void readQR(BufferedImage wideImage, BufferedImage croppedImage) {
        Result result = readQR(wideImage);
        if(result == null) {
            result = readQR(croppedImage);
        }
        if(result == null) {
            result = readQR(invert(croppedImage));
        }

        if(result != null) {
            resultProperty.set(result);
        }
    }

    private Result readQR(BufferedImage bufferedImage) {
        LuminanceSource source = new BufferedImageLuminanceSource(bufferedImage);
        BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));

        try {
            com.sparrowwallet.bokmakierie.Result result = bokmakierie.scan(bufferedImage);
            if(result != null) {
                return new Result(result.getMessage(), result.getRawBytes(), new ResultPoint[0], BarcodeFormat.QR_CODE);
            }
        } catch(Exception e) {
            log.debug("Error scanning QR", e);
        }

        if(ZBar.isEnabled()) {
            ZBar.Scan scan = ZBar.scan(bufferedImage);
            if(scan != null) {
                return new Result(scan.stringData(), scan.rawData(), new ResultPoint[0], BarcodeFormat.QR_CODE);
            }
        }

        try {
            return qrReader.decode(bitmap, Map.of(DecodeHintType.TRY_HARDER, Boolean.TRUE));
        } catch(ReaderException e) {
            // fall thru, it means there is no QR code in image
            return null;
        }
    }

    private BufferedImage getFramedImage(BufferedImage image, CroppedDimension cropped) {
        BufferedImage clone = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = (Graphics2D)clone.getGraphics();
        g2d.drawImage(image, 0, 0, null);
        float[] dash1 = {10.0f};
        g2d.setColor(Color.BLACK);
        g2d.setStroke(new BasicStroke(resolution == WebcamResolution.HD ? 3.0f : 1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10.0f, dash1, 0.0f));
        g2d.draw(new RoundRectangle2D.Double(cropped.x, cropped.y, cropped.length, cropped.length, 10, 10));
        g2d.dispose();
        return clone;
    }

    private CroppedDimension getCroppedDimension(BufferedImage bufferedImage) {
        int dimension = Math.min(bufferedImage.getWidth(), bufferedImage.getHeight());
        int squareSize = dimension / 2;
        int x = (bufferedImage.getWidth() - squareSize) / 2;
        int y = (bufferedImage.getHeight() - squareSize) / 2;
        return new CroppedDimension(x, y, squareSize);
    }

    public BufferedImage invert(BufferedImage inImg) {
        try {
            int width = inImg.getWidth();
            int height = inImg.getHeight();
            BufferedImage outImg = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            WritableRaster outRaster = outImg.getRaster();
            WritableRaster inRaster = inImg.getRaster();

            for(int y = 0; y < height; y++) {
                for(int x = 0; x < width; x++) {
                    for(int i = 0; i < outRaster.getNumBands(); i++) {
                        outRaster.setSample(x, y, i, 255 - inRaster.getSample(x, y, i));
                    }
                }
            }

            return outImg;
        } catch(Exception e) {
            log.warn("Error inverting image", e);
            return inImg;
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

    private static class CroppedDimension {
        public int x;
        public int y;
        public int length;

        public CroppedDimension(int x, int y, int length) {
            this.x = x;
            this.y = y;
            this.length = length;
        }
    }
}
