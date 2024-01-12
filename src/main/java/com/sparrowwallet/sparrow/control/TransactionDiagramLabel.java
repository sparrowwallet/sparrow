package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.drongo.wallet.*;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.glyphfont.FontAwesome5;
import com.sparrowwallet.sparrow.glyphfont.GlyphUtils;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.text.Font;
import org.controlsfx.glyphfont.Glyph;

import java.util.*;
import java.util.stream.Collectors;

public class TransactionDiagramLabel extends HBox {
    private final List<HBox> outputs = new ArrayList<>();
    private final Button left;
    private final Button right;
    private final IntegerProperty displayedIndex = new SimpleIntegerProperty(-1);

    public TransactionDiagramLabel() {
        setSpacing(5);
        setAlignment(Pos.CENTER_RIGHT);

        left = new Button("");
        left.setGraphic(getLeftGlyph());
        left.setOnAction(event -> {
            int index = displayedIndex.get();
            if(index > 0) {
                index--;
            }
            displayedIndex.set(index);
        });

        right = new Button("");
        right.setGraphic(getRightGlyph());
        right.setOnAction(event -> {
            int index = displayedIndex.get();
            if(index < outputs.size() - 1) {
                index++;
            }
            displayedIndex.set(index);
        });

        displayedIndex.addListener((observable, oldValue, newValue) -> {
            left.setDisable(newValue.intValue() <= 0);
            right.setDisable(newValue.intValue() < 0 || newValue.intValue() >= outputs.size() - 1);
            if(oldValue.intValue() >= 0 && oldValue.intValue() < outputs.size()) {
                outputs.get(oldValue.intValue()).setVisible(false);
            }
            if(newValue.intValue() >= 0 && newValue.intValue() < outputs.size()) {
                outputs.get(newValue.intValue()).setVisible(true);
            }
        });
    }

    public void update(TransactionDiagram transactionDiagram) {
        getChildren().clear();
        outputs.clear();
        displayedIndex.set(-1);
        double maxWidth = getMaxWidth();

        WalletTransaction walletTx = transactionDiagram.getWalletTransaction();
        List<OutputLabel> outputLabels = new ArrayList<>();

        List<Payment> premixOutputs = walletTx.getPayments().stream().filter(walletTx::isPremixSend).collect(Collectors.toList());
        if(!premixOutputs.isEmpty()) {
            OutputLabel premixOutputLabel = getPremixOutputLabel(transactionDiagram, premixOutputs);
            if(premixOutputLabel != null) {
                outputLabels.add(premixOutputLabel);
            }

            Optional<Payment> optWhirlpoolFee = walletTx.getPayments().stream().filter(payment -> payment.getType() == Payment.Type.WHIRLPOOL_FEE).findFirst();
            if(optWhirlpoolFee.isPresent()) {
                OutputLabel whirlpoolFeeOutputLabel = getWhirlpoolFeeOutputLabel(transactionDiagram, optWhirlpoolFee.get(), premixOutputs);
                outputLabels.add(whirlpoolFeeOutputLabel);
            }

            List<Payment> badbankOutputs = walletTx.getPayments().stream().filter(walletTx::isBadbankSend).collect(Collectors.toList());
            List<OutputLabel> badbankOutputLabels = badbankOutputs.stream().map(payment -> getBadbankOutputLabel(transactionDiagram, payment)).collect(Collectors.toList());
            outputLabels.addAll(badbankOutputLabels);
        } else if(walletTx.getPayments().size() >= 5 && walletTx.getPayments().stream().mapToLong(Payment::getAmount).distinct().count() <= 1 && walletTx.getWallet() != null
                && walletTx.getWallet().getStandardAccountType() == StandardAccount.WHIRLPOOL_PREMIX  && walletTx.getPayments().stream().anyMatch(walletTx::isPostmixSend)) {
            OutputLabel mixOutputLabel = getMixOutputLabel(transactionDiagram, walletTx.getPayments());
            if(mixOutputLabel != null) {
                outputLabels.add(mixOutputLabel);
            }
        } else if(walletTx.getPayments().size() >= 5 && walletTx.getPayments().stream().mapToLong(Payment::getAmount).distinct().count() <= 1 && walletTx.getWallet() != null
                && walletTx.getWallet().getStandardAccountType() == StandardAccount.WHIRLPOOL_POSTMIX  && walletTx.getPayments().stream().anyMatch(walletTx::isConsolidationSend)) {
            OutputLabel remixOutputLabel = getRemixOutputLabel(transactionDiagram, walletTx.getPayments());
            if(remixOutputLabel != null) {
                outputLabels.add(remixOutputLabel);
            }
        } else {
            List<Payment> payments = walletTx.getPayments().stream().filter(payment -> payment.getType() == Payment.Type.DEFAULT && !walletTx.isConsolidationSend(payment)).collect(Collectors.toList());
            List<OutputLabel> paymentLabels = payments.stream().map(payment -> getOutputLabel(transactionDiagram, payment)).collect(Collectors.toList());
            if(walletTx.getSelectedUtxos().values().stream().allMatch(Objects::isNull)) {
                paymentLabels.sort(Comparator.comparingInt(paymentLabel -> (paymentLabel.text.startsWith("Receive") ? 0 : 1)));
            }
            outputLabels.addAll(paymentLabels);

            List<Payment> consolidations = walletTx.getPayments().stream().filter(payment -> payment.getType() == Payment.Type.DEFAULT && walletTx.isConsolidationSend(payment)).collect(Collectors.toList());
            outputLabels.addAll(consolidations.stream().map(consolidation -> getOutputLabel(transactionDiagram, consolidation)).collect(Collectors.toList()));

            List<Payment> mixes = walletTx.getPayments().stream().filter(payment -> payment.getType() == Payment.Type.MIX || payment.getType() == Payment.Type.FAKE_MIX).collect(Collectors.toList());
            outputLabels.addAll(mixes.stream().map(payment -> getOutputLabel(transactionDiagram, payment)).collect(Collectors.toList()));
        }

        Map<WalletNode, Long> changeMap = walletTx.getChangeMap();
        outputLabels.addAll(changeMap.entrySet().stream().map(changeEntry -> getOutputLabel(transactionDiagram, changeEntry)).collect(Collectors.toList()));

        OutputLabel feeOutputLabel = getFeeOutputLabel(transactionDiagram);
        if(feeOutputLabel != null) {
            outputLabels.add(feeOutputLabel);
        }

        for(OutputLabel outputLabel : outputLabels) {
            maxWidth = Math.max(maxWidth, outputLabel.width);
            outputs.add(outputLabel.hBox);
            getChildren().add(outputLabel.hBox);
        }

        HBox buttonBox = new HBox();
        buttonBox.setAlignment(Pos.CENTER_RIGHT);
        buttonBox.getChildren().addAll(left, right);
        getChildren().add(buttonBox);

        setMaxWidth(maxWidth);
        setPrefWidth(maxWidth);

        if(outputLabels.size() > 0) {
            displayedIndex.set(0);
        }
    }

    private OutputLabel getPremixOutputLabel(TransactionDiagram transactionDiagram, List<Payment> premixOutputs) {
        if(premixOutputs.isEmpty()) {
            return null;
        }

        Payment premixOutput = premixOutputs.get(0);
        long total = premixOutputs.stream().mapToLong(Payment::getAmount).sum();
        Glyph glyph = GlyphUtils.getOutputGlyph(transactionDiagram.getWalletTransaction(), premixOutput);
        String text;
        if(premixOutputs.size() == 1) {
            text = "Premix transaction with 1 output of " + transactionDiagram.getSatsValue(premixOutput.getAmount()) + " sats";
        } else {
            text = "Premix transaction with " + premixOutputs.size() + " outputs of " + transactionDiagram.getSatsValue(premixOutput.getAmount()) + " sats each ("
                    + transactionDiagram.getSatsValue(total) + " sats)";
        }

        return getOutputLabel(glyph, text);
    }

    private OutputLabel getBadbankOutputLabel(TransactionDiagram transactionDiagram, Payment payment) {
        Glyph glyph = GlyphUtils.getOutputGlyph(transactionDiagram.getWalletTransaction(), payment);
        String text = "Badbank change of " + transactionDiagram.getSatsValue(payment.getAmount()) + " sats to " + payment.getAddress().toString();

        return getOutputLabel(glyph, text);
    }

    private OutputLabel getWhirlpoolFeeOutputLabel(TransactionDiagram transactionDiagram, Payment whirlpoolFee, List<Payment> premixOutputs) {
        long total = premixOutputs.stream().mapToLong(Payment::getAmount).sum();
        double feePercentage = (double)whirlpoolFee.getAmount() / (total - whirlpoolFee.getAmount());
        Glyph glyph = GlyphUtils.getOutputGlyph(transactionDiagram.getWalletTransaction(), whirlpoolFee);
        String text = "Whirlpool fee of " + transactionDiagram.getSatsValue(whirlpoolFee.getAmount()) + " sats (" + String.format("%.2f", feePercentage * 100.0) + "% of total premix value)";

        return getOutputLabel(glyph, text);
    }

    private OutputLabel getMixOutputLabel(TransactionDiagram transactionDiagram, List<Payment> mixOutputs) {
        if(mixOutputs.isEmpty()) {
            return null;
        }

        Payment remixOutput = mixOutputs.get(0);
        long total = mixOutputs.stream().mapToLong(Payment::getAmount).sum();
        Glyph glyph = GlyphUtils.getPremixGlyph();
        String text = "Mix transaction with " + mixOutputs.size() + " outputs of " + transactionDiagram.getSatsValue(remixOutput.getAmount()) + " sats each ("
                + transactionDiagram.getSatsValue(total) + " sats)";

        return getOutputLabel(glyph, text);
    }

    private OutputLabel getRemixOutputLabel(TransactionDiagram transactionDiagram, List<Payment> remixOutputs) {
        if(remixOutputs.isEmpty()) {
            return null;
        }

        Payment remixOutput = remixOutputs.get(0);
        long total = remixOutputs.stream().mapToLong(Payment::getAmount).sum();
        Glyph glyph = GlyphUtils.getPremixGlyph();
        String text = "Remix transaction with " + remixOutputs.size() + " outputs of " + transactionDiagram.getSatsValue(remixOutput.getAmount()) + " sats each ("
                + transactionDiagram.getSatsValue(total) + " sats)";

        return getOutputLabel(glyph, text);
    }

    private OutputLabel getOutputLabel(TransactionDiagram transactionDiagram, Payment payment) {
        WalletTransaction walletTx = transactionDiagram.getWalletTransaction();
        Wallet toWallet = walletTx.getToWallet(AppServices.get().getOpenWallets().keySet(), payment);
        WalletNode toNode = walletTx.getWallet() != null && !walletTx.getWallet().isBip47() ? walletTx.getAddressNodeMap().get(payment.getAddress()) : null;

        Glyph glyph = GlyphUtils.getOutputGlyph(transactionDiagram.getWalletTransaction(), payment);
        String text = (toWallet == null ? (toNode != null ? "Consolidate " : "Pay ") : "Receive ") + transactionDiagram.getSatsValue(payment.getAmount()) + " sats to " + payment.getAddress().toString();

        return getOutputLabel(glyph, text);
    }

    private OutputLabel getOutputLabel(TransactionDiagram transactionDiagram, Map.Entry<WalletNode, Long> changeEntry) {
        WalletTransaction walletTx = transactionDiagram.getWalletTransaction();

        Glyph glyph = GlyphUtils.getChangeGlyph();
        String text = "Change of " + transactionDiagram.getSatsValue(changeEntry.getValue()) + " sats to " + walletTx.getChangeAddress(changeEntry.getKey()).toString();

        return getOutputLabel(glyph, text);
    }

    private OutputLabel getFeeOutputLabel(TransactionDiagram transactionDiagram) {
        WalletTransaction walletTx = transactionDiagram.getWalletTransaction();
        if(walletTx.getFee() < 0) {
            return null;
        }

        Glyph glyph = GlyphUtils.getFeeGlyph();
        String text = "Fee of " + transactionDiagram.getSatsValue(walletTx.getFee()) + " sats (" + String.format("%.2f", walletTx.getFeePercentage() * 100.0) + "%)";

        return getOutputLabel(glyph, text);
    }

    private OutputLabel getOutputLabel(Glyph glyph, String text) {
        Label icon = new Label();
        icon.setMinWidth(15);
        glyph.setFontSize(12);
        icon.setGraphic(glyph);

        CopyableLabel label = new CopyableLabel();
        label.setFont(Font.font("Roboto Mono Italic", 13));
        label.setText(text);

        HBox output = new HBox(5);
        output.setAlignment(Pos.CENTER);
        output.managedProperty().bind(output.visibleProperty());
        output.setVisible(false);
        output.getChildren().addAll(icon, label);

        double lineWidth = TextUtils.computeTextWidth(label.getFont(), label.getText(), 0.0D) + 2 + getSpacing() + icon.getMinWidth() + 60;
        return new OutputLabel(output, lineWidth, text);
    }

    public static Glyph getLeftGlyph() {
        Glyph caretLeftGlyph = new Glyph(FontAwesome5.FONT_NAME, FontAwesome5.Glyph.CARET_LEFT);
        caretLeftGlyph.getStyleClass().add("label-left-icon");
        caretLeftGlyph.setFontSize(15);
        return caretLeftGlyph;
    }

    public static Glyph getRightGlyph() {
        Glyph caretRightGlyph = new Glyph(FontAwesome5.FONT_NAME, FontAwesome5.Glyph.CARET_RIGHT);
        caretRightGlyph.getStyleClass().add("label-right-icon");
        caretRightGlyph.setFontSize(15);
        return caretRightGlyph;
    }

    private record OutputLabel(HBox hBox, double width, String text) {}
}
