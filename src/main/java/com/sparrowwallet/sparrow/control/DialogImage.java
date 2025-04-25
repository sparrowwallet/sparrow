package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.Theme;
import com.sparrowwallet.sparrow.io.Config;
import javafx.beans.NamedArg;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.layout.StackPane;
import org.girod.javafx.svgimage.SVGImage;
import org.girod.javafx.svgimage.SVGLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.util.Locale;

public class DialogImage extends StackPane {
    private static final Logger log = LoggerFactory.getLogger(DialogImage.class);

    public static final int WIDTH = 50;
    public static final int HEIGHT = 50;

    public ObjectProperty<DialogImage.Type> typeProperty = new SimpleObjectProperty<>();

    public DialogImage() {
        setPrefSize(WIDTH, HEIGHT);
        this.typeProperty.addListener((observable, oldValue, type) -> {
            refresh(type);
        });
    }

    public DialogImage(@NamedArg("type") Type type) {
        this();
        this.typeProperty.set(type);
    }

    public void refresh() {
        Type type = getType();
        refresh(type);
    }

    protected void refresh(Type type) {
        SVGImage svgImage;
        if(Config.get().getTheme() == Theme.DARK) {
            svgImage = loadSVGImage("/image/dialog/" + type.name().toLowerCase(Locale.ROOT) + "-invert.svg");
        } else {
            svgImage = loadSVGImage("/image/dialog/" + type.name().toLowerCase(Locale.ROOT) + ".svg");
        }

        if(svgImage != null) {
            getChildren().clear();
            getChildren().add(svgImage);
        }
    }

    public Type getType() {
        return typeProperty.get();
    }

    public ObjectProperty<Type> typeProperty() {
        return typeProperty;
    }

    public void setType(Type type) {
        this.typeProperty.set(type);
    }

    private SVGImage loadSVGImage(String imageName) {
        try {
            URL url = AppServices.class.getResource(imageName);
            if(url != null) {
                return SVGLoader.load(url);
            }
        } catch(Exception e) {
            log.error("Could not find image " + imageName);
        }

        return null;
    }

    public enum Type {
        SPARROW, SEED, PAYNYM, BORDERWALLETS, USERADD, WHIRLPOOL;
    }
}
