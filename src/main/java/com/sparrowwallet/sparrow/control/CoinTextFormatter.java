package com.sparrowwallet.sparrow.control;

import javafx.scene.control.TextFormatter;
import javafx.scene.control.TextInputControl;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.ParseException;
import java.util.Locale;
import java.util.regex.Pattern;

public class CoinTextFormatter extends TextFormatter<String> {
    private static final Pattern COIN_VALIDATION = Pattern.compile("[\\d,]*(\\.\\d{0,8})?");
    public static final DecimalFormat COIN_FORMAT = new DecimalFormat("###,###.########", DecimalFormatSymbols.getInstance(Locale.ENGLISH));

    public CoinTextFormatter() {
        super(new CoinFilter());
    }

    private static class CoinFilter implements UnaryOperator<Change> {
        @Override
        public Change apply(Change change) {
            String oldText = change.getControlText();
            String newText = change.getControlNewText();
            String deleted = null;
            if(change.isDeleted()) {
                deleted = oldText.substring(change.getRangeStart(), change.getRangeEnd());
            }

            String noFractionCommaText = newText;
            int commasRemoved = 0;
            int dotIndex = newText.indexOf(".");
            if(dotIndex > -1) {
                noFractionCommaText = newText.substring(0, dotIndex) + newText.substring(dotIndex).replaceAll(",", "");
                commasRemoved = newText.length() - noFractionCommaText.length();
            }

            if(!COIN_VALIDATION.matcher(noFractionCommaText).matches()) {
                return null;
            }

            if(",".equals(change.getText())) {
                return null;
            }

            if("".equals(newText)) {
                return change;
            }

            if(change.isDeleted() && ",".equals(deleted) && change.getRangeStart() > 0) {
                noFractionCommaText = noFractionCommaText.substring(0, change.getRangeStart() - 1) + noFractionCommaText.substring(change.getRangeEnd() - 1);
            }

            try {
                Number value = COIN_FORMAT.parse(noFractionCommaText);
                String correct = COIN_FORMAT.format(value.doubleValue());

                String compare = newText;
                if(compare.contains(".") && compare.endsWith("0")) {
                    compare = compare.replaceAll("0*$", "");
                }

                if(compare.endsWith(".")) {
                    compare = compare.substring(0, compare.length() - 1);
                }

                if(correct.equals(compare)) {
                    return change;
                }

                if(value.doubleValue() == 0.0 && "0".equals(correct)) {
                    return change;
                }

                TextInputControl control = (TextInputControl)change.getControl();
                change.setText(correct);
                change.setRange(0, control.getLength());

                if(correct.length() != newText.length()) {
                    String postCorrect = correct.substring(Math.min(change.getCaretPosition(), correct.length()));
                    int commasAfter = postCorrect.length() - postCorrect.replace(",", "").length();
                    int caretShift = change.isDeleted() && ".".equals(deleted) ? commasAfter : 0;

                    int caret = change.getCaretPosition() + (correct.length() - newText.length() - caretShift) + commasRemoved;
                    if(caret >= 0) {
                        change.setCaretPosition(caret);
                        change.setAnchor(caret);
                    }
                }

                return change;
            } catch (ParseException e) {
                return null;
            }
        }
    }
}
