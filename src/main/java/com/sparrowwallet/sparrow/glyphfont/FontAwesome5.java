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
        ADJUST('\uf042'),
        ARROW_CIRCLE_DOWN('\uf0ab'),
        ANGLE_DOUBLE_RIGHT('\uf101'),
        ARROW_DOWN('\uf063'),
        ARROW_UP('\uf062'),
        BAN('\uf05e'),
        BIOHAZARD('\uf780'),
        BTC('\uf15a'),
        BULLSEYE('\uf140'),
        CAMERA('\uf030'),
        CHECK_CIRCLE('\uf058'),
        CIRCLE('\uf111'),
        COINS('\uf51e'),
        COPY('\uf0c5'),
        EXCHANGE_ALT('\uf362'),
        EXCLAMATION_CIRCLE('\uf06a'),
        EXCLAMATION_TRIANGLE('\uf071'),
        EXTERNAL_LINK_ALT('\uf35d'),
        ELLIPSIS_H('\uf141'),
        EYE('\uf06e'),
        FEATHER_ALT('\uf56b'),
        FILE_CSV('\uf6dd'),
        HAND_HOLDING('\uf4bd'),
        HAND_HOLDING_MEDICAL('\ue05c'),
        HAND_HOLDING_WATER('\uf4c1'),
        HISTORY('\uf1da'),
        INFO_CIRCLE('\uf05a'),
        KEY('\uf084'),
        LAPTOP('\uf109'),
        LOCK('\uf023'),
        LOCK_OPEN('\uf3c1'),
        MINUS_CIRCLE('\uf056'),
        PEN_FANCY('\uf5ac'),
        PLUS('\uf067'),
        PLAY_CIRCLE('\uf144'),
        PLUS_CIRCLE('\uf055'),
        STOP_CIRCLE('\uf28d'),
        QRCODE('\uf029'),
        QUESTION_CIRCLE('\uf059'),
        RANDOM('\uf074'),
        REPLY_ALL('\uf122'),
        SATELLITE_DISH('\uf7c0'),
        SD_CARD('\uf7c2'),
        SEARCH('\uf002'),
        SIGN_OUT_ALT('\uf2f5'),
        SQUARE('\uf0c8'),
        SNOWFLAKE('\uf2dc'),
        SORT_NUMERIC_DOWN('\uf162'),
        SUN('\uf185'),
        THEATER_MASKS('\uf630'),
        TIMES_CIRCLE('\uf057'),
        TOGGLE_OFF('\uf204'),
        TOGGLE_ON('\uf205'),
        TOOLS('\uf7d9'),
        UNDO('\uf0e2'),
        USER('\uf007'),
        USER_FRIENDS('\uf500'),
        USER_PLUS('\uf234'),
        USER_SLASH('\uf506'),
        WALLET('\uf555'),
        WEIGHT('\uf496');

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
