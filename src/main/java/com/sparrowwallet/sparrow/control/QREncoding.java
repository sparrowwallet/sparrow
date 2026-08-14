package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.Theme;
import com.sparrowwallet.sparrow.glyphfont.FontAwesome5;
import com.sparrowwallet.sparrow.io.Config;
import javafx.geometry.Insets;
import javafx.scene.Node;
import org.controlsfx.glyphfont.Glyph;
import org.girod.javafx.svgimage.SVGLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.util.Locale;

public enum QREncoding {
    UR("UR"), BBQR("BBQr"), RAW("Raw");

    private static final Logger log = LoggerFactory.getLogger(QREncoding.class);

    QREncoding(String name) {
        this.name = name;
    }

    private final String name;

    public String getName() {
        return name;
    }

    public Node getSVGImage() {
        try {
            URL url = AppServices.class.getResource("/image/qrencoding/" + getName().toLowerCase(Locale.ROOT) + "-icon" + (Config.get().getTheme() == Theme.DARK ? "-invert" : "") + ".svg");
            if(url != null) {
                return SVGLoader.load(url);
            } else {
                Glyph glyph = new Glyph(FontAwesome5.FONT_NAME, FontAwesome5.Glyph.QRCODE);
                glyph.setFontSize(12);
                glyph.setPadding(new Insets(0, 2, 0, 0));
                return glyph;
            }
        } catch(Exception e) {
            log.error("Could not load QR encoding source image for " + name);
        }

        return null;
    }
}
