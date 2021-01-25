package com.sparrowwallet.sparrow.glyphfont;

import org.controlsfx.glyphfont.FontAwesome;
import org.controlsfx.glyphfont.GlyphFont;
import org.controlsfx.glyphfont.GlyphFontRegistry;
import org.controlsfx.glyphfont.INamedCharacter;

import java.io.InputStream;
import java.util.Arrays;

public class FontAwesome5 extends GlyphFont {
    public static String FONT_NAME = "Font Awesome 5 Free Solid";

    /**
     * The individual glyphs offered by the FontAwesome5 font.
     */
    public static enum Glyph implements INamedCharacter {
        ANGLE_DOUBLE_RIGHT('\uf101'),
        ARROW_DOWN('\uf063'),
        ARROW_UP('\uf062'),
        BTC('\uf15a'),
        CAMERA('\uf030'),
        CHECK_CIRCLE('\uf058'),
        CIRCLE('\uf111'),
        COINS('\uf51e'),
        EXCHANGE_ALT('\uf362'),
        EXCLAMATION_CIRCLE('\uf06a'),
        EXCLAMATION_TRIANGLE('\uf071'),
        EXTERNAL_LINK_ALT('\uf35d'),
        ELLIPSIS_H('\uf141'),
        EYE('\uf06e'),
        HAND_HOLDING('\uf4bd'),
        HAND_HOLDING_MEDICAL('\ue05c'),
        HISTORY('\uf1da'),
        KEY('\uf084'),
        LAPTOP('\uf109'),
        LOCK('\uf023'),
        LOCK_OPEN('\uf3c1'),
        PEN_FANCY('\uf5ac'),
        PLUS('\uf067'),
        QRCODE('\uf029'),
        QUESTION_CIRCLE('\uf059'),
        RANDOM('\uf074'),
        REPLY_ALL('\uf122'),
        SATELLITE_DISH('\uf7c0'),
        SD_CARD('\uf7c2'),
        SEARCH('\uf002'),
        SQUARE('\uf0c8'),
        TIMES_CIRCLE('\uf057'),
        TOGGLE_OFF('\uf204'),
        TOGGLE_ON('\uf205'),
        TOOLS('\uf7d9'),
        UNDO('\uf0e2'),
        WALLET('\uf555');

        private final char ch;

        /**
         * Creates a named Glyph mapped to the given character
         * @param ch
         */
        Glyph( char ch ) {
            this.ch = ch;
        }

        @Override
        public char getChar() {
            return ch;
        }
    }

    /**
     * Do not call this constructor directly - instead access the
     * {@link FontAwesome5.Glyph} public static enumeration method to create the glyph nodes), or
     * use the {@link GlyphFontRegistry} class to get access.
     *
     * Note: Do not remove this public constructor since it is used by the service loader!
     */
    public FontAwesome5() {
        this(FontAwesome5.class.getResourceAsStream("/font/fa-solid-900.ttf"));
    }

    /**
     * Creates a new FontAwesome instance which uses the provided font source.
     * @param is
     */
    public FontAwesome5(InputStream is){
        super(FONT_NAME, 14, is, true);
        registerAll(Arrays.asList(FontAwesome.Glyph.values()));
        registerAll(Arrays.asList(FontAwesome5.Glyph.values()));
    }
}
