package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.drongo.protocol.Sha256Hash;
import com.sparrowwallet.drongo.wallet.BlockTransactionHashIndex;
import com.sparrowwallet.drongo.wallet.WalletNode;
import com.sparrowwallet.drongo.wallet.WalletTransaction;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.event.ExcludeUtxoEvent;
import com.sparrowwallet.sparrow.glyphfont.FontAwesome5;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.*;
import javafx.scene.shape.CubicCurve;
import javafx.scene.shape.Line;
import org.controlsfx.glyphfont.FontAwesome;
import org.controlsfx.glyphfont.Glyph;

import java.util.*;

public class TransactionDiagram extends GridPane {
    private static final int MAX_UTXOS = 8;
    private static final double DIAGRAM_HEIGHT = 230.0;

    private WalletTransaction walletTx;

    public void update(WalletTransaction walletTx) {
        if(walletTx == null) {
            getChildren().clear();
        } else {
            this.walletTx = walletTx;
            update();
        }
    }

    public void update() {
        Map<BlockTransactionHashIndex, WalletNode> displayedUtxos = getDisplayedUtxos();

        Pane inputsTypePane = getInputsType(displayedUtxos);
        GridPane.setConstraints(inputsTypePane, 0, 0);

        Pane inputsPane = getInputsLabels(displayedUtxos);
        GridPane.setConstraints(inputsPane, 1, 0);

        Node inputsLinesPane = getInputsLines(displayedUtxos);
        GridPane.setConstraints(inputsLinesPane, 2, 0);

        Pane txPane = getTransactionPane();
        GridPane.setConstraints(txPane, 3, 0);

        Pane outputsLinesPane = getOutputsLines();
        GridPane.setConstraints(outputsLinesPane, 4, 0);

        Pane outputsPane = getOutputsLabels();
        GridPane.setConstraints(outputsPane, 5, 0);

        getChildren().clear();
        getChildren().addAll(inputsTypePane, inputsPane, inputsLinesPane, txPane, outputsLinesPane, outputsPane);
    }

    private Map<BlockTransactionHashIndex, WalletNode> getDisplayedUtxos() {
        Map<BlockTransactionHashIndex, WalletNode> selectedUtxos = walletTx.getSelectedUtxos();

        if(selectedUtxos.size() > MAX_UTXOS) {
            Map<BlockTransactionHashIndex, WalletNode> utxos = new LinkedHashMap<>();
            List<BlockTransactionHashIndex> additional = new ArrayList<>();
            for(BlockTransactionHashIndex reference : selectedUtxos.keySet()) {
                if (utxos.size() < MAX_UTXOS) {
                    utxos.put(reference, selectedUtxos.get(reference));
                } else {
                    additional.add(reference);
                }
            }

            utxos.put(new AdditionalBlockTransactionHashIndex(additional), null);
            return utxos;
        } else {
            return selectedUtxos;
        }
    }

    private Pane getInputsType(Map<BlockTransactionHashIndex, WalletNode> displayedUtxos) {
        StackPane stackPane = new StackPane();

        if(walletTx.isCoinControlUsed()) {
            VBox pane = new VBox();
            double width = 22.0;
            Group group = new Group();
            VBox.setVgrow(group, Priority.ALWAYS);

            Line widthLine = new Line();
            widthLine.setStartX(0);
            widthLine.setEndX(width);
            widthLine.getStyleClass().add("boundary");

            Line topYaxis = new Line();
            topYaxis.setStartX(width * 0.5);
            topYaxis.setStartY(DIAGRAM_HEIGHT * 0.5 - 20.0);
            topYaxis.setEndX(width * 0.5);
            topYaxis.setEndY(0);
            topYaxis.getStyleClass().add("inputs-type");

            Line topBracket = new Line();
            topBracket.setStartX(width * 0.5);
            topBracket.setStartY(0);
            topBracket.setEndX(width);
            topBracket.setEndY(0);
            topBracket.getStyleClass().add("inputs-type");

            Line bottomYaxis = new Line();
            bottomYaxis.setStartX(width * 0.5);
            bottomYaxis.setStartY(DIAGRAM_HEIGHT);
            bottomYaxis.setEndX(width * 0.5);
            bottomYaxis.setEndY(DIAGRAM_HEIGHT * 0.5 + 20.0);
            bottomYaxis.getStyleClass().add("inputs-type");

            Line bottomBracket = new Line();
            bottomBracket.setStartX(width * 0.5);
            bottomBracket.setStartY(DIAGRAM_HEIGHT);
            bottomBracket.setEndX(width);
            bottomBracket.setEndY(DIAGRAM_HEIGHT);
            bottomBracket.getStyleClass().add("inputs-type");

            group.getChildren().addAll(widthLine, topYaxis, topBracket, bottomYaxis, bottomBracket);
            pane.getChildren().add(group);

            Glyph lockGlyph = getLockGlyph();
            lockGlyph.getStyleClass().add("inputs-type");
            Tooltip tooltip = new Tooltip("Coin control active");
            lockGlyph.setTooltip(tooltip);
            stackPane.getChildren().addAll(pane, lockGlyph);
        }

        return stackPane;
    }

    private Pane getInputsLabels(Map<BlockTransactionHashIndex, WalletNode> displayedUtxos) {
        VBox inputsBox = new VBox();
        inputsBox.setMaxWidth(150);
        inputsBox.setPadding(new Insets(0, 10, 0, 10));
        inputsBox.minHeightProperty().bind(minHeightProperty());
        inputsBox.setAlignment(Pos.CENTER_RIGHT);
        inputsBox.getChildren().add(createSpacer());
        for(BlockTransactionHashIndex input : displayedUtxos.keySet()) {
            WalletNode walletNode = displayedUtxos.get(input);
            String desc = getInputDescription(input);
            Label label = new Label(desc);
            label.getStyleClass().add("utxo-label");

            Button excludeUtxoButton = new Button("");
            excludeUtxoButton.setGraphic(getExcludeGlyph());
            excludeUtxoButton.setOnAction(event -> {
                EventManager.get().post(new ExcludeUtxoEvent(walletTx, input));
            });

            label.setGraphic(excludeUtxoButton);
            label.setContentDisplay(ContentDisplay.LEFT);

            Tooltip tooltip = new Tooltip();
            if(walletNode != null) {
                tooltip.setText("Spending " + getSatsValue(input.getValue()) + " sats from " + walletNode.getDerivationPath() + "\n" + input.getHashAsString() + ":" + input.getIndex() + "\n" + walletTx.getWallet().getAddress(walletNode));
                if(input.getLabel() == null || input.getLabel().isEmpty()) {
                    label.getStyleClass().add("input-label");
                } else {
                    tooltip.getStyleClass().add("input-label");
                }
            } else {
                AdditionalBlockTransactionHashIndex additionalReference = (AdditionalBlockTransactionHashIndex)input;
                StringJoiner joiner = new StringJoiner("\n");
                for(BlockTransactionHashIndex additionalInput : additionalReference.getAdditionalInputs()) {
                    joiner.add(getInputDescription(additionalInput));
                }
                tooltip.setText(joiner.toString());
                tooltip.getStyleClass().add("input-label");
            }
            label.setTooltip(tooltip);

            inputsBox.getChildren().add(label);
            inputsBox.getChildren().add(createSpacer());
        }

        return inputsBox;
    }

    private String getInputDescription(BlockTransactionHashIndex input) {
        return input.getLabel() != null && !input.getLabel().isEmpty() ? input.getLabel() : input.getHashAsString().substring(0, 8) + "..:" + input.getIndex();
    }

    private String getSatsValue(long amount) {
        return String.format(Locale.ENGLISH, "%,d", amount);
    }

    private Pane getInputsLines(Map<BlockTransactionHashIndex, WalletNode> displayedUtxos) {
        VBox pane = new VBox();
        Group group = new Group();
        VBox.setVgrow(group, Priority.ALWAYS);

        Line yaxisLine = new Line();
        yaxisLine.setStartX(0);
        yaxisLine.setStartY(0);
        yaxisLine.setEndX(0);
        yaxisLine.setEndY(DIAGRAM_HEIGHT);
        yaxisLine.getStyleClass().add("boundary");
        group.getChildren().add(yaxisLine);

        double width = 140.0;
        int numUtxos = displayedUtxos.size();
        for(int i = 1; i <= numUtxos; i++) {
            CubicCurve curve = new CubicCurve();
            curve.getStyleClass().add("input-line");

            curve.setStartX(0);
            double scaleFactor = (double)i / (numUtxos + 1);
            int nodeHeight = 17;
            double additional = (0.5 - scaleFactor) * ((double)nodeHeight);
            curve.setStartY(scale(DIAGRAM_HEIGHT, scaleFactor, additional));
            curve.setEndX(width);
            curve.setEndY(scale(DIAGRAM_HEIGHT, 0.5, 0));

            curve.setControlX1(scale(width, 0.2, 0));
            curve.setControlY1(curve.getStartY());
            curve.setControlX2(scale(width, 0.8, 0));
            curve.setControlY2(curve.getEndY());

            group.getChildren().add(curve);
        }

        pane.getChildren().add(group);
        return pane;
    }

    private static double scale(Double value, double scaleFactor, double additional) {
        return value * (1.0 - scaleFactor) + additional;
    }

    private Pane getOutputsLines() {
        VBox pane = new VBox();
        Group group = new Group();
        VBox.setVgrow(group, Priority.ALWAYS);

        Line yaxisLine = new Line();
        yaxisLine.setStartX(0);
        yaxisLine.setStartY(0);
        yaxisLine.setEndX(0);
        yaxisLine.endYProperty().bind(this.heightProperty());
        yaxisLine.getStyleClass().add("boundary");
        group.getChildren().add(yaxisLine);

        double width = 140.0;
        int numOutputs = (walletTx.getChangeNode() == null ? 2 : 3);
        for(int i = 1; i <= numOutputs; i++) {
            CubicCurve curve = new CubicCurve();
            curve.getStyleClass().add("output-line");

            curve.setStartX(0);
            curve.setStartY(scale(DIAGRAM_HEIGHT, 0.5, 0));
            curve.setEndX(width);
            double scaleFactor = (double)i / (numOutputs + 1);
            int nodeHeight = 20;
            double additional = (0.5 - scaleFactor) * ((double)nodeHeight);
            curve.setEndY(scale(DIAGRAM_HEIGHT, scaleFactor, additional));

            curve.setControlX1(scale(width, 0.2, 0));
            curve.controlY1Property().bind(curve.startYProperty());
            curve.setControlX2(scale(width, 0.8, 0));
            curve.controlY2Property().bind(curve.endYProperty());

            group.getChildren().add(curve);
        }

        pane.getChildren().add(group);
        return pane;
    }

    private Pane getOutputsLabels() {
        VBox outputsBox = new VBox();
        outputsBox.setMaxWidth(150);
        outputsBox.setPadding(new Insets(0, 20, 0, 10));
        outputsBox.setAlignment(Pos.CENTER_LEFT);
        outputsBox.getChildren().add(createSpacer());

        boolean isConsolidation = walletTx.isConsolidationSend();
        String recipientDesc = walletTx.getRecipientAddress().toString().substring(0, 8) + "...";
        Label recipientLabel = new Label(recipientDesc, isConsolidation ? getConsolidationGlyph() : getPaymentGlyph());
        recipientLabel.getStyleClass().addAll("output-label", "recipient-label");
        Tooltip recipientTooltip = new Tooltip((isConsolidation ? "Consolidate " : "Pay ") + getSatsValue(walletTx.getRecipientAmount()) + " sats to\n" + walletTx.getRecipientAddress().toString());
        recipientLabel.setTooltip(recipientTooltip);
        outputsBox.getChildren().add(recipientLabel);
        outputsBox.getChildren().add(createSpacer());

        if(walletTx.getChangeNode() != null) {
            String changeDesc = walletTx.getChangeAddress().toString().substring(0, 8) + "...";
            Label changeLabel = new Label(changeDesc, getChangeGlyph());
            changeLabel.getStyleClass().addAll("output-label", "change-label");
            Tooltip changeTooltip = new Tooltip("Change of " + getSatsValue(walletTx.getChangeAmount()) + " sats to " + walletTx.getChangeNode().getDerivationPath() + "\n" + walletTx.getChangeAddress().toString());
            changeLabel.setTooltip(changeTooltip);
            outputsBox.getChildren().add(changeLabel);
            outputsBox.getChildren().add(createSpacer());
        }

        boolean highFee = (walletTx.getFeePercentage() > 0.1);
        Label feeLabel = highFee ? new Label("High Fee", getWarningGlyph()) : new Label("Fee", getFeeGlyph());
        feeLabel.getStyleClass().addAll("output-label", "fee-label");
        String percentage = String.format("%.2f", walletTx.getFeePercentage() * 100.0);
        Tooltip feeTooltip = new Tooltip("Fee of " + getSatsValue(walletTx.getFee()) + " sats (" + percentage + "%)");
        feeTooltip.getStyleClass().add("fee-tooltip");
        feeLabel.setTooltip(feeTooltip);
        outputsBox.getChildren().add(feeLabel);
        outputsBox.getChildren().add(createSpacer());

        return outputsBox;
    }

    private Pane getTransactionPane() {
        VBox txPane = new VBox();
        txPane.setPadding(new Insets(0, 10, 0, 10));
        txPane.setAlignment(Pos.CENTER);
        txPane.getChildren().add(createSpacer());

        String txDesc = "Transaction";
        Label txLabel = new Label(txDesc);
        Tooltip tooltip = new Tooltip(walletTx.getTransaction().getLength() + " bytes\n" + walletTx.getTransaction().getVirtualSize() + " vBytes");
        txLabel.setTooltip(tooltip);
        txPane.getChildren().add(txLabel);
        txPane.getChildren().add(createSpacer());

        return txPane;
    }

    private Node createSpacer() {
        final Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        return spacer;
    }

    public static Glyph getExcludeGlyph() {
        Glyph excludeGlyph = new Glyph(FontAwesome5.FONT_NAME, FontAwesome5.Glyph.TIMES_CIRCLE);
        excludeGlyph.getStyleClass().add("exclude-utxo");
        excludeGlyph.setFontSize(12);
        return excludeGlyph;
    }

    public static Glyph getPaymentGlyph() {
        Glyph paymentGlyph = new Glyph("FontAwesome", FontAwesome.Glyph.SEND);
        paymentGlyph.getStyleClass().add("payment-icon");
        paymentGlyph.setFontSize(12);
        return paymentGlyph;
    }

    public static Glyph getConsolidationGlyph() {
        Glyph consolidationGlyph = new Glyph(FontAwesome5.FONT_NAME, FontAwesome5.Glyph.REPLY_ALL);
        consolidationGlyph.getStyleClass().add("consolidation-icon");
        consolidationGlyph.setFontSize(12);
        return consolidationGlyph;
    }

    public static Glyph getChangeGlyph() {
        Glyph changeGlyph = new Glyph(FontAwesome5.FONT_NAME, FontAwesome5.Glyph.COINS);
        changeGlyph.getStyleClass().add("change-icon");
        changeGlyph.setFontSize(12);
        return changeGlyph;
    }

    private Glyph getFeeGlyph() {
        Glyph feeGlyph = new Glyph(FontAwesome5.FONT_NAME, FontAwesome5.Glyph.HAND_HOLDING);
        feeGlyph.getStyleClass().add("fee-icon");
        feeGlyph.setFontSize(12);
        return feeGlyph;
    }

    private Glyph getWarningGlyph() {
        Glyph feeWarningGlyph = new Glyph(FontAwesome5.FONT_NAME, FontAwesome5.Glyph.EXCLAMATION_CIRCLE);
        feeWarningGlyph.getStyleClass().add("fee-warning-icon");
        feeWarningGlyph.setFontSize(12);
        return feeWarningGlyph;
    }

    private Glyph getLockGlyph() {
        Glyph lockGlyph = new Glyph(FontAwesome5.FONT_NAME, FontAwesome5.Glyph.LOCK);
        lockGlyph.getStyleClass().add("lock-icon");
        lockGlyph.setFontSize(12);
        return lockGlyph;
    }

    private static class AdditionalBlockTransactionHashIndex extends BlockTransactionHashIndex {
        private final List<BlockTransactionHashIndex> additionalInputs;

        public AdditionalBlockTransactionHashIndex(List<BlockTransactionHashIndex> additionalInputs) {
            super(Sha256Hash.ZERO_HASH, 0, new Date(), 0L, 0, 0);
            this.additionalInputs = additionalInputs;
        }

        @Override
        public String getLabel() {
            return additionalInputs.size() + " more...";
        }

        public List<BlockTransactionHashIndex> getAdditionalInputs() {
            return additionalInputs;
        }
    }
}
