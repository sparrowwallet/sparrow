package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.drongo.BitcoinUnit;
import com.sparrowwallet.sparrow.UnitFormat;
import javafx.scene.control.TextFormatter;
import javafx.scene.control.TextInputControl;

import java.text.DecimalFormat;
import java.text.ParseException;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CoinTextFormatter extends TextFormatter<String> {
    public CoinTextFormatter(UnitFormat unitFormat, BitcoinUnit bitcoinUnit) {
        super(new CoinFilter(unitFormat == null ? UnitFormat.DOT : unitFormat, bitcoinUnit));
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
        private final Pattern anyPrecisionAmount;

        public CoinFilter(UnitFormat unitFormat, BitcoinUnit bitcoinUnit) {
            this.unitFormat = unitFormat;
            this.coinFormat = new DecimalFormat("###,###.########", unitFormat.getDecimalFormatSymbols());
            String integer = "[\\d" + Pattern.quote(unitFormat.getGroupingSeparator()) + "]*";
            //A satoshi is indivisible, so a sats amount has no fractional part to validate
            String fraction = bitcoinUnit == BitcoinUnit.SATOSHIS ? "" : "(" + Pattern.quote(unitFormat.getDecimalSeparator()) + "\\d{0,8})?";
            this.coinValidation = Pattern.compile(integer + fraction);
            this.anyPrecisionAmount = Pattern.compile(integer + "(" + Pattern.quote(unitFormat.getDecimalSeparator()) + "\\d*)?");
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

            boolean validAmount = coinValidation.matcher(noFractionCommaText).matches();
            if(!validAmount) {
                //The amount a pasted text starts with is taken, cut to the places the unit has. A digit typed beyond the last place is ignored instead,
                //leaving what has been typed as it was rather than rewriting it
                Matcher leadingAmount = anyPrecisionAmount.matcher(noFractionCommaText);
                Matcher amount = coinValidation.matcher(leadingAmount.find() ? leadingAmount.group() : "");
                if(amount.matches() || (change.getText().length() > 1 && amount.lookingAt())) {
                    noFractionCommaText = amount.group();
                } else {
                    return null;
                }
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

                //Trailing fractional zeros and a trailing separator are left as typed so the fraction can still be entered, but only where the entire text is a valid amount
                String compare = newText;
                if(validAmount && compare.contains(unitFormat.getDecimalSeparator()) && compare.endsWith("0")) {
                    compare = compare.replaceAll("0*$", "");
                }

                if(validAmount && compare.endsWith(unitFormat.getDecimalSeparator())) {
                    compare = compare.substring(0, compare.length() - 1);
                }

                if(correct.equals(compare)) {
                    return change;
                }

                //A zero value is left as entered so the fractional part can still be typed out, but only where the entire text is a valid amount
                if(validAmount && value.doubleValue() == 0.0 && "0".equals(correct)) {
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
