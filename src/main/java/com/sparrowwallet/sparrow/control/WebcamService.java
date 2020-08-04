package com.sparrowwallet.sparrow.control;

import com.github.sarxos.webcam.Webcam;
import com.github.sarxos.webcam.WebcamResolution;
import com.google.zxing.*;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;

import java.awt.image.BufferedImage;
import java.util.concurrent.TimeUnit;

public class WebcamService extends Service<Image> {
    private final WebcamResolution resolution ;

    private final ObjectProperty<Result> resultProperty = new SimpleObjectProperty<>(null);

    public WebcamService(WebcamResolution resolution) {
        this.resolution = resolution;
    }

    @Override
    public Task<Image> createTask() {
        return new Task<Image>() {
            @Override
            protected Image call() throws Exception {
                Webcam cam = Webcam.getWebcams(1, TimeUnit.MINUTES).get(0);
                try {
                    cam.setCustomViewSizes(resolution.getSize());
                    cam.setViewSize(resolution.getSize());

                    cam.open();
                    while(!isCancelled()) {
                        if(cam.isImageNew()) {
                            BufferedImage bimg = cam.getImage();
                            updateValue(SwingFXUtils.toFXImage(bimg, null));
                            readQR(bimg);
                        }
                    }
                    cam.close();
                    return getValue();
                } finally {
                    cam.close();
                }
            }
        };
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
}
