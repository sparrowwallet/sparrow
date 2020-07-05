package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.drongo.protocol.Sha256Hash;
import com.sparrowwallet.drongo.wallet.BlockTransactionHashIndex;
import com.sparrowwallet.drongo.wallet.WalletNode;
import com.sparrowwallet.drongo.wallet.WalletTransaction;
import com.sparrowwallet.sparrow.glyphfont.FontAwesome5;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.*;
import javafx.scene.shape.CubicCurve;
import javafx.scene.shape.Line;
import org.controlsfx.glyphfont.FontAwesome;
import org.controlsfx.glyphfont.Glyph;

import java.util.*;

public class TransactionDiagram extends GridPane {
    private static final int MAX_UTXOS = 5;

    private WalletTransaction walletTx;

    public TransactionDiagram() {
        int columns = 5;
        double[] percentWidth = {20, 20, 10, 20, 30};

        for(int i = 0; i < columns; i++) {
            ColumnConstraints columnConstraints = new ColumnConstraints();
            columnConstraints.setPercentWidth(percentWidth[i]);
            getColumnConstraints().add(columnConstraints);
        }
    }

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

        Pane inputsPane = getInputsLabels(displayedUtxos);
        GridPane.setConstraints(inputsPane, 0, 0);

        Pane inputsLinesPane = getInputsLines(displayedUtxos);
        GridPane.setConstraints(inputsLinesPane, 1, 0);

        Pane txPane = getTransactionPane();
        GridPane.setConstraints(txPane, 2, 0);

        Pane outputsLinesPane = getOutputsLines();
        GridPane.setConstraints(outputsLinesPane, 3, 0);

        Pane outputsPane = getOutputsLabels();
        GridPane.setConstraints(outputsPane, 4, 0);

        getChildren().clear();
        getChildren().addAll(inputsPane, inputsLinesPane, txPane, outputsLinesPane, outputsPane);
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

    private Pane getInputsLabels(Map<BlockTransactionHashIndex, WalletNode> displayedUtxos) {
        VBox inputsBox = new VBox();
        inputsBox.setPadding(new Insets(0, 10, 0, 10));
        inputsBox.minHeightProperty().bind(minHeightProperty());
        inputsBox.setAlignment(Pos.CENTER_RIGHT);
        inputsBox.getChildren().add(createSpacer());
        for(BlockTransactionHashIndex input : displayedUtxos.keySet()) {
            WalletNode walletNode = displayedUtxos.get(input);
            String desc = getInputDescription(input);
            Label label = new Label(desc);

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
        yaxisLine.endYProperty().bind(this.heightProperty());
        yaxisLine.getStyleClass().add("y-axis");
        group.getChildren().add(yaxisLine);

        int numUtxos = displayedUtxos.size();
        for(int i = 1; i <= numUtxos; i++) {
            CubicCurve curve = new CubicCurve();
            curve.getStyleClass().add("input-line");

            curve.setStartX(0);
            curve.startYProperty().bind(getScaledProperty(this.heightProperty(), (double)i / (numUtxos + 1), 20));
            curve.endXProperty().bind(pane.widthProperty());
            curve.endYProperty().bind(getScaledProperty(this.heightProperty(), 0.5, 0));

            curve.controlX1Property().bind(getScaledProperty(pane.widthProperty(), 0.2, 0));
            curve.controlY1Property().bind(curve.startYProperty());
            curve.controlX2Property().bind(getScaledProperty(pane.widthProperty(), 0.8, 0));
            curve.controlY2Property().bind(curve.endYProperty());

            group.getChildren().add(curve);
        }

        pane.getChildren().add(group);
        return pane;
    }

    private static DoubleProperty getScaledProperty(ReadOnlyDoubleProperty property, double scaleFactor, int nodeHeight) {
        SimpleDoubleProperty scaledProperty = new SimpleDoubleProperty(scale(property.doubleValue(), scaleFactor, nodeHeight));
        property.addListener((observable, oldValue, newValue) -> {
            scaledProperty.set(scale(newValue.doubleValue(), scaleFactor, nodeHeight));
        });

        return scaledProperty;
    }

    private static double scale(Double value, double scaleFactor, int nodeHeight) {
        double scaled = value * (1.0 - scaleFactor);
        if(nodeHeight > 0) {
            scaled += (0.5 - scaleFactor) * ( (double)nodeHeight );
        }

        return scaled;
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
        yaxisLine.getStyleClass().add("y-axis");
        group.getChildren().add(yaxisLine);

        int numOutputs = (walletTx.getChangeNode() == null ? 2 : 3);
        for(int i = 1; i <= numOutputs; i++) {
            CubicCurve curve = new CubicCurve();
            curve.getStyleClass().add("output-line");

            curve.setStartX(0);
            curve.startYProperty().bind(getScaledProperty(this.heightProperty(), 0.5, 0));
            curve.endXProperty().bind(pane.widthProperty());
            curve.endYProperty().bind(getScaledProperty(this.heightProperty(), (double)i / (numOutputs + 1), 20));

            curve.controlX1Property().bind(getScaledProperty(pane.widthProperty(), 0.2, 0));
            curve.controlY1Property().bind(curve.startYProperty());
            curve.controlX2Property().bind(getScaledProperty(pane.widthProperty(), 0.8, 0));
            curve.controlY2Property().bind(curve.endYProperty());

            group.getChildren().add(curve);
        }

        pane.getChildren().add(group);
        return pane;
    }

    private Pane getOutputsLabels() {
        VBox outputsBox = new VBox();
        outputsBox.setPadding(new Insets(0, 30, 0, 10));
        outputsBox.setAlignment(Pos.CENTER_LEFT);
        outputsBox.getChildren().add(createSpacer());

        String recipientDesc = walletTx.getRecipientAddress().toString().substring(0, 8) + "...";
        Label recipientLabel = new Label(recipientDesc, getSendGlyph());
        recipientLabel.getStyleClass().addAll("output-label", "recipient-label");
        Tooltip recipientTooltip = new Tooltip("Send " + getSatsValue(walletTx.getRecipientAmount()) + " sats to\n" + walletTx.getRecipientAddress().toString());
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
        String feeDesc = "Fee";
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

    private Glyph getSendGlyph() {
        Glyph sendGlyph = new Glyph("FontAwesome", FontAwesome.Glyph.SEND);
        sendGlyph.getStyleClass().add("send-icon");
        sendGlyph.setFontSize(12);
        return sendGlyph;
    }

    private Glyph getChangeGlyph() {
        Glyph changeGlyph = new Glyph("Font Awesome 5 Free Solid", FontAwesome5.Glyph.COINS);
        changeGlyph.getStyleClass().add("change-icon");
        changeGlyph.setFontSize(12);
        return changeGlyph;
    }

    private Glyph getFeeGlyph() {
        Glyph feeGlyph = new Glyph("Font Awesome 5 Free Solid", FontAwesome5.Glyph.HAND_HOLDING);
        feeGlyph.getStyleClass().add("fee-icon");
        feeGlyph.setFontSize(12);
        return feeGlyph;
    }

    private Glyph getWarningGlyph() {
        Glyph feeWarningGlyph = new Glyph("Font Awesome 5 Free Solid", FontAwesome5.Glyph.EXCLAMATION_CIRCLE);
        feeWarningGlyph.getStyleClass().add("fee-warning-icon");
        feeWarningGlyph.setFontSize(12);
        return feeWarningGlyph;
    }

    private static class AdditionalBlockTransactionHashIndex extends BlockTransactionHashIndex {
        private final List<BlockTransactionHashIndex> additionalInputs;

        public AdditionalBlockTransactionHashIndex(List<BlockTransactionHashIndex> additionalInputs) {
            super(Sha256Hash.ZERO_HASH, 0, new Date(), 0L, 0, 0);
            this.additionalInputs = additionalInputs;
        }

        @Override
        public String getLabel() {
            return additionalInputs.size() + " more";
        }

        public List<BlockTransactionHashIndex> getAdditionalInputs() {
            return additionalInputs;
        }
    }
}
