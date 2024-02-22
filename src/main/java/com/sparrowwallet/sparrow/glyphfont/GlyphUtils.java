package com.sparrowwallet.sparrow.glyphfont;

import com.sparrowwallet.drongo.wallet.Payment;
import com.sparrowwallet.drongo.wallet.WalletTransaction;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.control.TransactionDiagram;
import org.controlsfx.glyphfont.FontAwesome;
import org.controlsfx.glyphfont.Glyph;

public class GlyphUtils {
    public static Glyph getOutputGlyph(WalletTransaction walletTx, Payment payment) {
        if(payment.getType().equals(Payment.Type.MIX)) {
            return getMixGlyph();
        } else if(payment.getType().equals(Payment.Type.FAKE_MIX)) {
            return getFakeMixGlyph();
        } else if(walletTx.isConsolidationSend(payment)) {
            return getConsolidationGlyph();
        } else if(walletTx.isPremixSend(payment)) {
            return getPremixGlyph();
        } else if(walletTx.isBadbankSend(payment)) {
            return getBadbankGlyph();
        } else if(payment.getType().equals(Payment.Type.WHIRLPOOL_FEE)) {
            return getWhirlpoolFeeGlyph();
        } else if(payment instanceof TransactionDiagram.AdditionalPayment) {
            return ((TransactionDiagram.AdditionalPayment)payment).getOutputGlyph(walletTx);
        } else if(walletTx.getToWallet(AppServices.get().getOpenWallets().keySet(), payment) != null) {
            return getDepositGlyph();
        } else if(walletTx.isDuplicateAddress(payment)) {
            return getPaymentWarningGlyph();
        }

        return getPaymentGlyph();
    }

    public static Glyph getPaymentGlyph() {
        Glyph paymentGlyph = new Glyph("FontAwesome", FontAwesome.Glyph.SEND);
        paymentGlyph.getStyleClass().add("payment-icon");
        paymentGlyph.setFontSize(12);
        return paymentGlyph;
    }

    public static Glyph getPaymentWarningGlyph() {
        Glyph paymentWarningGlyph = new Glyph(FontAwesome5.FONT_NAME, FontAwesome5.Glyph.EXCLAMATION_TRIANGLE);
        paymentWarningGlyph.getStyleClass().add("payment-warning-icon");
        paymentWarningGlyph.setFontSize(12);
        return paymentWarningGlyph;
    }

    public static Glyph getConsolidationGlyph() {
        Glyph consolidationGlyph = new Glyph(FontAwesome5.FONT_NAME, FontAwesome5.Glyph.REPLY_ALL);
        consolidationGlyph.getStyleClass().add("consolidation-icon");
        consolidationGlyph.setFontSize(12);
        return consolidationGlyph;
    }

    public static Glyph getDepositGlyph() {
        Glyph depositGlyph = new Glyph(FontAwesome5.FONT_NAME, FontAwesome5.Glyph.ARROW_DOWN);
        depositGlyph.getStyleClass().add("deposit-icon");
        depositGlyph.setFontSize(12);
        return depositGlyph;
    }

    public static Glyph getPremixGlyph() {
        Glyph premixGlyph = new Glyph(FontAwesome5.FONT_NAME, FontAwesome5.Glyph.RANDOM);
        premixGlyph.getStyleClass().add("premix-icon");
        premixGlyph.setFontSize(12);
        return premixGlyph;
    }

    public static Glyph getBadbankGlyph() {
        Glyph badbankGlyph = new Glyph(FontAwesome5.FONT_NAME, FontAwesome5.Glyph.BIOHAZARD);
        badbankGlyph.getStyleClass().add("badbank-icon");
        badbankGlyph.setFontSize(12);
        return badbankGlyph;
    }

    public static Glyph getWhirlpoolFeeGlyph() {
        Glyph whirlpoolFeeGlyph = new Glyph(FontAwesome5.FONT_NAME, FontAwesome5.Glyph.HAND_HOLDING_WATER);
        whirlpoolFeeGlyph.getStyleClass().add("whirlpoolfee-icon");
        whirlpoolFeeGlyph.setFontSize(12);
        return whirlpoolFeeGlyph;
    }

    public static Glyph getFakeMixGlyph() {
        Glyph fakeMixGlyph = new Glyph(FontAwesome5.FONT_NAME, FontAwesome5.Glyph.THEATER_MASKS);
        fakeMixGlyph.getStyleClass().add("fakemix-icon");
        fakeMixGlyph.setFontSize(12);
        return fakeMixGlyph;
    }

    public static Glyph getTxoGlyph() {
        return getChangeGlyph();
    }

    public static Glyph getMixGlyph() {
        Glyph payjoinGlyph = new Glyph(FontAwesome5.FONT_NAME, FontAwesome5.Glyph.RANDOM);
        payjoinGlyph.getStyleClass().add("mix-icon");
        payjoinGlyph.setFontSize(12);
        return payjoinGlyph;
    }

    public static Glyph getExternalInputGlyph() {
        Glyph externalGlyph = new Glyph(FontAwesome5.FONT_NAME, FontAwesome5.Glyph.LONG_ARROW_ALT_RIGHT);
        externalGlyph.getStyleClass().add("external-input-icon");
        externalGlyph.setFontSize(12);
        return externalGlyph;
    }

    public static Glyph getExcludeGlyph() {
        Glyph excludeGlyph = new Glyph(FontAwesome5.FONT_NAME, FontAwesome5.Glyph.TIMES_CIRCLE);
        excludeGlyph.getStyleClass().add("exclude-utxo");
        excludeGlyph.setFontSize(12);
        return excludeGlyph;
    }

    public static Glyph getChangeGlyph() {
        Glyph changeGlyph = new Glyph(FontAwesome5.FONT_NAME, FontAwesome5.Glyph.COINS);
        changeGlyph.getStyleClass().add("change-icon");
        changeGlyph.setFontSize(12);
        return changeGlyph;
    }

    public static Glyph getChangeWarningGlyph() {
        Glyph changeWarningGlyph = new Glyph(FontAwesome5.FONT_NAME, FontAwesome5.Glyph.EXCLAMATION_TRIANGLE);
        changeWarningGlyph.getStyleClass().add("change-warning-icon");
        changeWarningGlyph.setFontSize(12);
        return changeWarningGlyph;
    }

    public static Glyph getChangeReplaceGlyph() {
        Glyph changeReplaceGlyph = new Glyph(FontAwesome5.FONT_NAME, FontAwesome5.Glyph.ARROW_DOWN);
        changeReplaceGlyph.getStyleClass().add("change-replace-icon");
        changeReplaceGlyph.setFontSize(12);
        return changeReplaceGlyph;
    }

    public static Glyph getFeeGlyph() {
        Glyph feeGlyph = new Glyph(FontAwesome5.FONT_NAME, FontAwesome5.Glyph.HAND_HOLDING);
        feeGlyph.getStyleClass().add("fee-icon");
        feeGlyph.setFontSize(12);
        return feeGlyph;
    }

    public static Glyph getFeeWarningGlyph() {
        Glyph feeWarningGlyph = new Glyph(FontAwesome5.FONT_NAME, FontAwesome5.Glyph.EXCLAMATION_CIRCLE);
        feeWarningGlyph.getStyleClass().add("fee-warning-icon");
        feeWarningGlyph.setFontSize(12);
        return feeWarningGlyph;
    }

    public static Glyph getQuestionGlyph() {
        Glyph feeWarningGlyph = new Glyph(FontAwesome5.FONT_NAME, FontAwesome5.Glyph.QUESTION_CIRCLE);
        feeWarningGlyph.getStyleClass().add("question-icon");
        feeWarningGlyph.setFontSize(12);
        return feeWarningGlyph;
    }

    public static Glyph getLockGlyph() {
        Glyph lockGlyph = new Glyph(FontAwesome5.FONT_NAME, FontAwesome5.Glyph.LOCK);
        lockGlyph.getStyleClass().add("lock-icon");
        lockGlyph.setFontSize(12);
        return lockGlyph;
    }

    public static Glyph getUserGlyph() {
        Glyph userGlyph = new Glyph(FontAwesome5.FONT_NAME, FontAwesome5.Glyph.USER);
        userGlyph.getStyleClass().add("user-icon");
        userGlyph.setFontSize(12);
        return userGlyph;
    }

    public static Glyph getOpcodeGlyph() {
        Glyph userGlyph = new Glyph(FontAwesome5.FONT_NAME, FontAwesome5.Glyph.MICROCHIP);
        userGlyph.getStyleClass().add("opcode-icon");
        userGlyph.setFontSize(12);
        return userGlyph;
    }

    public static Glyph getSuccessGlyph() {
        Glyph successGlyph = new Glyph(FontAwesome5.FONT_NAME, FontAwesome5.Glyph.CHECK_CIRCLE);
        successGlyph.getStyleClass().add("success");
        successGlyph.setFontSize(12);
        return successGlyph;
    }

    public static Glyph getWarningGlyph() {
        Glyph warningGlyph = new Glyph(FontAwesome5.FONT_NAME, FontAwesome5.Glyph.EXCLAMATION_TRIANGLE);
        warningGlyph.getStyleClass().add("warn-icon");
        warningGlyph.setFontSize(12);
        return warningGlyph;
    }

    public static Glyph getFailureGlyph() {
        Glyph failureGlyph = new Glyph(FontAwesome5.FONT_NAME, FontAwesome5.Glyph.TIMES_CIRCLE);
        failureGlyph.getStyleClass().add("failure");
        failureGlyph.setFontSize(12);
        return failureGlyph;
    }

    public static Glyph getBusyGlyph() {
        Glyph busyGlyph = new Glyph(FontAwesome5.FONT_NAME, FontAwesome5.Glyph.HOURGLASS_HALF);
        busyGlyph.getStyleClass().add("busy");
        busyGlyph.setFontSize(12);
        return busyGlyph;
    }
}
