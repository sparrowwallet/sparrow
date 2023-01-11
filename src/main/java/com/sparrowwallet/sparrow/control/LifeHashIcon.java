package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.drongo.Utils;
import com.sparrowwallet.sparrow.io.ImageUtils;
import com.sparrowwallet.toucan.LifeHash;
import com.sparrowwallet.toucan.LifeHashVersion;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Group;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.scene.paint.ImagePattern;
import javafx.scene.shape.Rectangle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.image.BufferedImage;
import java.util.Arrays;

public class LifeHashIcon extends Group {
    private static final Logger log = LoggerFactory.getLogger(LifeHashIcon.class);

    private static final int SIZE = 24;

    private final ObjectProperty<byte[]> dataProperty = new SimpleObjectProperty<>(null);

    public LifeHashIcon() {
        super();

        dataProperty.addListener((observable, oldValue, data) -> {
            if(data == null) {
                getChildren().clear();
            } else if(oldValue == null || !Arrays.equals(oldValue, data)) {
                LifeHash.Image lifeHashImage = LifeHash.makeFromData(data, LifeHashVersion.VERSION2, 1, false);
                BufferedImage bufferedImage = LifeHash.getBufferedImage(lifeHashImage);
                BufferedImage resizedImage = ImageUtils.resizeToImage(bufferedImage, SIZE, SIZE);
                Image image = SwingFXUtils.toFXImage(resizedImage, null);
                setImage(image);
            }
        });
    }

    private void setImage(Image image) {
        getChildren().clear();
        Rectangle rectangle = new Rectangle(SIZE, SIZE);
        rectangle.setArcWidth(6);
        rectangle.setArcHeight(6);
        rectangle.setFill(new ImagePattern(image));
        rectangle.setStroke(Color.rgb(65, 72, 77));
        rectangle.setStrokeWidth(1.0);
        getChildren().add(rectangle);
    }

    public byte[] getData() {
        return dataProperty.get();
    }

    public ObjectProperty<byte[]> dataProperty() {
        return dataProperty;
    }

    public void setData(byte[] data) {
        this.dataProperty.set(data);
    }

    public void setHex(String hex) {
        setData(hex == null ? null : Utils.hexToBytes(hex));
    }
}
