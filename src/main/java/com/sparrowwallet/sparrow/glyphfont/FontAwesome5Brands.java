package com.sparrowwallet.sparrow.glyphfont;

import org.controlsfx.glyphfont.FontAwesome;
import org.controlsfx.glyphfont.GlyphFont;
import org.controlsfx.glyphfont.GlyphFontRegistry;
import org.controlsfx.glyphfont.INamedCharacter;

import java.io.InputStream;
import java.util.Arrays;

public class FontAwesome5Brands extends GlyphFont {
    public static String FONT_NAME = "Font Awesome 5 Brands Regular";

    /**
     * The individual glyphs offered by the FontAwesome5Brands font.
     */
    public static enum Glyph implements INamedCharacter {
        USB('\uf287');

        private final char ch;

        /**
         * Creates a named Glyph mapped to the given character
         *
         * @param ch
         */
        Glyph(char ch) {
            this.ch = ch;
        }

        @Override
        public char getChar() {
            return ch;
        }
    }

    /**
     * Do not call this constructor directly - instead access the
     * {@link FontAwesome5Brands.Glyph} public static enumeration method to create the glyph nodes), or
     * use the {@link GlyphFontRegistry} class to get access.
     * <p>
     * Note: Do not remove this public constructor since it is used by the service loader!
     */
    public FontAwesome5Brands() {
        this(FontAwesome5Brands.class.getResourceAsStream("/font/fa-brands-400.ttf"));
    }

    /**
     * Creates a new FontAwesome5Brands instance which uses the provided font source.
     *
     * @param is
     */
    public FontAwesome5Brands(InputStream is) {
        super(FONT_NAME, 14, is, true);
        registerAll(Arrays.asList(FontAwesome5Brands.Glyph.values()));
    }
}
