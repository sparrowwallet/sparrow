package com.sparrowwallet.sparrow.control;

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
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.ScheduledService;
import javafx.concurrent.Task;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;
import net.sourceforge.zbar.ZBar;
import org.openpnp.capture.CaptureDevice;
import org.openpnp.capture.CaptureFormat;
import org.openpnp.capture.CaptureStream;
import org.openpnp.capture.OpenPnpCapture;
import org.openpnp.capture.library.OpenpnpCaptureLibrary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class WebcamService extends ScheduledService<Image> {
    private static final Logger log = LoggerFactory.getLogger(WebcamService.class);

    private WebcamResolution resolution;
    private CaptureDevice device;
    private final BooleanProperty opening = new SimpleBooleanProperty(false);
    private final BooleanProperty closed = new SimpleBooleanProperty(false);

    private final ObjectProperty<Result> resultProperty = new SimpleObjectProperty<>(null);

    private static final int QR_SAMPLE_PERIOD_MILLIS = 200;

    private final OpenPnpCapture capture;
    private CaptureStream stream;
    private long lastQrSampleTime;
    private final ObservableList<CaptureDevice> foundDevices = FXCollections.observableList(new ArrayList<>());
    private final Reader qrReader;
    private final Bokmakierie bokmakierie;

    static {
        OpenpnpCaptureLibrary.INSTANCE.Cap_installCustomLogFunction((level, ptr) -> {
            switch(level) {
                case 0:
                case 1:
                case 2:
                case 3:
                    log.error(ptr.getString(0).trim());
                    break;
                case 4:
                case 5:
                case 6:
                    log.info(ptr.getString(0).trim());
                    break;
                case 7:
                    log.debug(ptr.getString(0).trim());
                    break;
                case 8:
                    log.trace(ptr.getString(0).trim());
                    break;
            }
        });
    }

    public WebcamService(WebcamResolution requestedResolution, CaptureDevice requestedDevice) {
        this.capture = new OpenPnpCapture();
        this.resolution = requestedResolution;
        this.device = requestedDevice;
        this.lastQrSampleTime = System.currentTimeMillis();
        this.qrReader = new QRCodeReader();
        this.bokmakierie = new Bokmakierie();
    }

    @Override
    public Task<Image> createTask() {
        return new Task<>() {
            @Override
            protected Image call() throws Exception {
                try {
                    if(stream == null) {
                        List<CaptureDevice> devices = capture.getDevices();

                        List<CaptureDevice> newDevices = new ArrayList<>(devices);
                        newDevices.removeAll(foundDevices);
                        foundDevices.addAll(newDevices);
                        foundDevices.removeIf(device -> !devices.contains(device));

                        if(foundDevices.isEmpty()) {
                            throw new UnsupportedOperationException("No cameras available");
                        }

                        CaptureDevice selectedDevice = foundDevices.getFirst();

                        if(device != null) {
                            for(CaptureDevice webcam : foundDevices) {
                                if(webcam.getName().equals(device.getName())) {
                                    selectedDevice = webcam;
                                }
                            }
                        } else if(Config.get().getWebcamDevice() != null) {
                            for(CaptureDevice webcam : foundDevices) {
                                if(webcam.getName().equals(Config.get().getWebcamDevice())) {
                                    selectedDevice = webcam;
                                }
                            }
                        }

                        device = selectedDevice;

                        if(device.getFormats().isEmpty()) {
                            throw new UnsupportedOperationException("No resolutions supported by camera " + device.getName());
                        }

                        Map<WebcamResolution, CaptureFormat> supportedResolutions = device.getFormats().stream()
                                .filter(f -> WebcamResolution.from(f) != null)
                                .collect(Collectors.toMap(WebcamResolution::from, Function.identity(), (u, v) -> u));

                        CaptureFormat format = supportedResolutions.get(resolution);
                        if(format == null) {
                            if(!supportedResolutions.isEmpty()) {
                                format = supportedResolutions.values().iterator().next();
                            } else {
                                format = device.getFormats().getFirst();
                            }

                            log.warn("Could not get requested capture resolution, using " + format.getFormatInfo().width + "x" + format.getFormatInfo().height);
                        }

                        opening.set(true);
                        stream = device.openStream(format);
                        opening.set(false);
                        closed.set(false);
                    }

                    BufferedImage originalImage = stream.capture();
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
        stream = null;
        super.reset();
    }

    @Override
    public boolean cancel() {
        if(stream != null) {
            stream.close();
            closed.set(true);
        }

        return super.cancel();
    }

    public void close() {
        capture.close();
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
        return resolution.getWidth();
    }

    public int getCamHeight() {
        return resolution.getHeight();
    }

    public void setResolution(WebcamResolution resolution) {
        this.resolution = resolution;
    }

    public CaptureDevice getDevice() {
        return device;
    }

    public void setDevice(CaptureDevice device) {
        this.device = device;
    }

    public ObservableList<CaptureDevice> getFoundDevices() {
        return foundDevices;
    }

    public BooleanProperty openingProperty() {
        return opening;
    }

    public BooleanProperty closedProperty() {
        return closed;
    }

    public static String fourCCToString(int fourCC) {
        return new String(new char[] {
                (char) (fourCC >> 24 & 0xFF),
                (char) ((fourCC >> 16) & 0xFF),
                (char) ((fourCC >> 8) & 0xFF),
                (char) ((fourCC) & 0xFF)
        });
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
