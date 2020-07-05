package com.sparrowwallet.sparrow.control;

import java.text.DecimalFormatSymbols;
import java.util.regex.Pattern;

import javafx.beans.NamedArg;
import javafx.scene.control.TextFormatter;
import javafx.scene.control.TextFormatter.Change;

public class TextFieldValidator {

    private static final String CURRENCY_SYMBOL   = DecimalFormatSymbols.getInstance().getCurrencySymbol();
    private static final String DECIMAL_SEPARATOR = ".";

    private final Pattern       INPUT_PATTERN;

    public TextFieldValidator(@NamedArg("modus") ValidationModus modus, @NamedArg("countOf") int countOf) {
        this(modus.createPattern(countOf));
    }

    public TextFieldValidator(@NamedArg("regex") String regex) {
        this(Pattern.compile(regex));
    }

    public TextFieldValidator(Pattern inputPattern) {
        INPUT_PATTERN = inputPattern;
    }

    public static TextFieldValidator maxFractionDigits(int countOf) {
        return new TextFieldValidator(maxFractionPattern(countOf));
    }

    public static TextFieldValidator maxIntegers(int countOf) {
        return new TextFieldValidator(maxIntegerPattern(countOf));
    }

    public static TextFieldValidator integersOnly() {
        return new TextFieldValidator(integersOnlyPattern());
    }

    public TextFormatter<String> getFormatter() {
        return new TextFormatter<>(TextFormatter.IDENTITY_STRING_CONVERTER, "", this::validateChange);
    }

    private Change validateChange(Change c) {
        if (validate(c.getControlNewText())) {
            return c;
        }
        return null;
    }

    public boolean validate(String input) {
        return INPUT_PATTERN.matcher(input).matches();
    }

    private static Pattern maxFractionPattern(int countOf) {
        return Pattern.compile("\\d*(\\" + DECIMAL_SEPARATOR + "\\d{0," + countOf + "})?");
    }

    private static Pattern maxCurrencyFractionPattern(int countOf) {
        return Pattern.compile("^\\\\" + CURRENCY_SYMBOL + "?\\s?\\d*(\\" + DECIMAL_SEPARATOR + "\\d{0," + countOf + "})?\\s?\\\\" + CURRENCY_SYMBOL + "?");
    }

    private static Pattern maxIntegerPattern(int countOf) {
        return Pattern.compile("\\d{0," + countOf + "}");
    }

    private static Pattern integersOnlyPattern() {
        return Pattern.compile("\\d*");
    }

    public enum ValidationModus {
        MAX_CURRENCY_FRACTION_DIGITS {
            @Override
            public Pattern createPattern(int countOf) {
                return maxCurrencyFractionPattern(countOf);
            }
        },

        MAX_FRACTION_DIGITS {
            @Override
            public Pattern createPattern(int countOf) {
                return maxFractionPattern(countOf);
            }
        },
        MAX_INTEGERS {
            @Override
            public Pattern createPattern(int countOf) {
                return maxIntegerPattern(countOf);
            }
        },

        INTEGERS_ONLY {
            @Override
            public Pattern createPattern(int countOf) {
                return integersOnlyPattern();
            }
        };

        public abstract Pattern createPattern(int countOf);
    }
}
