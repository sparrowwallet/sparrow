package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.sparrow.UnitFormat;
import javafx.scene.control.TextFormatter;
import javafx.scene.control.TextInputControl;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.ParseException;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;

public class CoinTextFormatter extends TextFormatter<String> {
    public CoinTextFormatter(UnitFormat unitFormat) {
        super(new CoinFilter(unitFormat == null ? UnitFormat.DOT : unitFormat));
    }

    public UnitFormat getUnitFormat() {
        return ((CoinFilter)getFilter()).unitFormat;
    }

    public DecimalFormat getCoinFormat() {
        return ((CoinFilter)getFilter()).coinFormat;
    }

    private static class CoinFilter implements UnaryOperator<Change> {
        private final UnitFormat unitFormat;
        private final DecimalFormat coinFormat;
        private final Pattern coinValidation;

        public CoinFilter(UnitFormat unitFormat) {
            this.unitFormat = unitFormat;
            this.coinFormat = new DecimalFormat("###,###.########", unitFormat.getDecimalFormatSymbols());
            this.coinValidation = Pattern.compile("[\\d" + Pattern.quote(unitFormat.getGroupingSeparator()) + "]*(" + Pattern.quote(unitFormat.getDecimalSeparator()) + "\\d{0,8})?");
        }

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
            int dotIndex = newText.indexOf(unitFormat.getDecimalSeparator());
            if(dotIndex > -1) {
                noFractionCommaText = newText.substring(0, dotIndex) + newText.substring(dotIndex).replaceAll(Pattern.quote(unitFormat.getGroupingSeparator()), "");
                commasRemoved = newText.length() - noFractionCommaText.length();
            }

            if(!coinValidation.matcher(noFractionCommaText).matches()) {
                return null;
            }

            if(unitFormat.getGroupingSeparator().equals(change.getText())) {
                return null;
            }

            if("".equals(newText)) {
                return change;
            }

            if(change.isDeleted() && unitFormat.getGroupingSeparator().equals(deleted) && change.getRangeStart() > 0) {
                noFractionCommaText = noFractionCommaText.substring(0, change.getRangeStart() - 1) + noFractionCommaText.substring(change.getRangeEnd() - 1);
            }

            try {
                Number value = coinFormat.parse(noFractionCommaText);
                String correct = coinFormat.format(value.doubleValue());

                String compare = newText;
                if(compare.contains(unitFormat.getDecimalSeparator()) && compare.endsWith("0")) {
                    compare = compare.replaceAll("0*$", "");
                }

                if(compare.endsWith(unitFormat.getDecimalSeparator())) {
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
                    int commasAfter = postCorrect.length() - postCorrect.replace(unitFormat.getGroupingSeparator(), "").length();
                    int caretShift = change.isDeleted() && unitFormat.getDecimalSeparator().equals(deleted) ? commasAfter : 0;

                    int caret = change.getCaretPosition() + (correct.length() - newText.length() - caretShift) + commasRemoved;
                    if(caret >= 0 && caret <= change.getControlNewText().length()) {
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
