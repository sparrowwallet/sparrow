package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.drongo.KeyPurpose;
import com.sparrowwallet.drongo.address.Address;
import com.sparrowwallet.drongo.protocol.Sha256Hash;
import com.sparrowwallet.drongo.uri.BitcoinURI;
import com.sparrowwallet.drongo.wallet.BlockTransactionHashIndex;
import com.sparrowwallet.drongo.wallet.Payment;
import com.sparrowwallet.drongo.wallet.WalletNode;
import com.sparrowwallet.drongo.wallet.WalletTransaction;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.event.ExcludeUtxoEvent;
import com.sparrowwallet.sparrow.event.ReplaceChangeAddressEvent;
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
import javafx.util.Duration;
import org.controlsfx.glyphfont.FontAwesome;
import org.controlsfx.glyphfont.Glyph;

import java.util.*;
import java.util.stream.Collectors;

public class TransactionDiagram extends GridPane {
    private static final int MAX_UTXOS = 7;
    private static final int MAX_PAYMENTS = 5;
    private static final double DIAGRAM_HEIGHT = 215.0;
    private static final int TOOLTIP_SHOW_DELAY = 50;

    private WalletTransaction walletTx;

    public void update(WalletTransaction walletTx) {
        setMinHeight(getDiagramHeight());
        setMaxHeight(getDiagramHeight());

        if(walletTx == null) {
            getChildren().clear();
        } else {
            this.walletTx = walletTx;
            update();
        }
    }

    public void update(String message) {
        setMinHeight(getDiagramHeight());
        setMaxHeight(getDiagramHeight());

        getChildren().clear();

        VBox messagePane = new VBox();
        messagePane.setPrefHeight(getDiagramHeight());
        messagePane.setPadding(new Insets(0, 10, 0, 280));
        messagePane.setAlignment(Pos.CENTER);
        messagePane.getChildren().add(createSpacer());

        Label messageLabel = new Label(message);
        messagePane.getChildren().add(messageLabel);
        messagePane.getChildren().add(createSpacer());

        GridPane.setConstraints(messagePane, 3, 0);
        getChildren().add(messagePane);
    }

    public void clear() {
        getChildren().clear();
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

        List<Payment> displayedPayments = getDisplayedPayments();

        Pane outputsLinesPane = getOutputsLines(displayedPayments);
        GridPane.setConstraints(outputsLinesPane, 4, 0);

        Pane outputsPane = getOutputsLabels(displayedPayments);
        GridPane.setConstraints(outputsPane, 5, 0);

        getChildren().clear();
        getChildren().addAll(inputsTypePane, inputsPane, inputsLinesPane, txPane, outputsLinesPane, outputsPane);
    }

    private Map<BlockTransactionHashIndex, WalletNode> getDisplayedUtxos() {
        Map<BlockTransactionHashIndex, WalletNode> selectedUtxos = walletTx.getSelectedUtxos();

        if(getPayjoinURI() != null) {
            selectedUtxos = new LinkedHashMap<>(selectedUtxos);
            selectedUtxos.put(new PayjoinBlockTransactionHashIndex(), null);
        }

        if(selectedUtxos.size() > MAX_UTXOS) {
            Map<BlockTransactionHashIndex, WalletNode> utxos = new LinkedHashMap<>();
            List<BlockTransactionHashIndex> additional = new ArrayList<>();
            for(BlockTransactionHashIndex reference : selectedUtxos.keySet()) {
                if(utxos.size() < MAX_UTXOS - 1) {
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

    private BitcoinURI getPayjoinURI() {
        for(Payment payment : walletTx.getPayments()) {
            try {
                Address address = payment.getAddress();
                BitcoinURI bitcoinURI = AppServices.getPayjoinURI(address);
                if(bitcoinURI != null) {
                    return bitcoinURI;
                }
            } catch(Exception e) {
                //ignore
            }
        }

        return null;
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
            topYaxis.setStartY(getDiagramHeight() * 0.5 - 20.0);
            topYaxis.setEndX(width * 0.5);
            topYaxis.setEndY(10);
            topYaxis.getStyleClass().add("inputs-type");

            Line topBracket = new Line();
            topBracket.setStartX(width * 0.5);
            topBracket.setStartY(10);
            topBracket.setEndX(width);
            topBracket.setEndY(10);
            topBracket.getStyleClass().add("inputs-type");

            Line bottomYaxis = new Line();
            bottomYaxis.setStartX(width * 0.5);
            bottomYaxis.setStartY(getDiagramHeight() - 10);
            bottomYaxis.setEndX(width * 0.5);
            bottomYaxis.setEndY(getDiagramHeight() * 0.5 + 20.0);
            bottomYaxis.getStyleClass().add("inputs-type");

            Line bottomBracket = new Line();
            bottomBracket.setStartX(width * 0.5);
            bottomBracket.setStartY(getDiagramHeight() - 10);
            bottomBracket.setEndX(width);
            bottomBracket.setEndY(getDiagramHeight() - 10);
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
        inputsBox.setPrefWidth(150);
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

            Tooltip tooltip = new Tooltip();
            if(walletNode != null) {
                tooltip.setText("Spending " + getSatsValue(input.getValue()) + " sats from " + walletNode.getDerivationPath().replace("m", "..") + "\n" + input.getHashAsString() + ":" + input.getIndex() + "\n" + walletTx.getWallet().getAddress(walletNode));
                tooltip.getStyleClass().add("input-label");

                if(input.getLabel() == null || input.getLabel().isEmpty()) {
                    label.getStyleClass().add("input-label");
                }

                label.setGraphic(excludeUtxoButton);
                label.setContentDisplay(ContentDisplay.LEFT);
            } else {
                if(input instanceof PayjoinBlockTransactionHashIndex) {
                    tooltip.setText("Added once transaction is signed and sent to the payjoin server");
                } else {
                    AdditionalBlockTransactionHashIndex additionalReference = (AdditionalBlockTransactionHashIndex) input;
                    StringJoiner joiner = new StringJoiner("\n");
                    for(BlockTransactionHashIndex additionalInput : additionalReference.getAdditionalInputs()) {
                        joiner.add(getInputDescription(additionalInput));
                    }
                    tooltip.setText(joiner.toString());
                }
                tooltip.getStyleClass().add("input-label");
            }
            tooltip.setShowDelay(new Duration(TOOLTIP_SHOW_DELAY));
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
        yaxisLine.setEndY(getDiagramHeight());
        yaxisLine.getStyleClass().add("boundary");
        group.getChildren().add(yaxisLine);

        double width = 140.0;
        List<BlockTransactionHashIndex> inputs = new ArrayList<>(displayedUtxos.keySet());
        int numUtxos = displayedUtxos.size();
        for(int i = 1; i <= numUtxos; i++) {
            CubicCurve curve = new CubicCurve();
            curve.getStyleClass().add("input-line");

            if(inputs.get(numUtxos-i) instanceof PayjoinBlockTransactionHashIndex) {
                curve.getStyleClass().add("input-dashed-line");
            }

            curve.setStartX(0);
            double scaleFactor = (double)i / (numUtxos + 1);
            int nodeHeight = 17;
            double additional = (0.5 - scaleFactor) * ((double)nodeHeight);
            curve.setStartY(scale(getDiagramHeight(), scaleFactor, additional));
            curve.setEndX(width);
            curve.setEndY(scale(getDiagramHeight(), 0.5, 0));

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

    private List<Payment> getDisplayedPayments() {
        List<Payment> payments = walletTx.getPayments();

        if(payments.size() > MAX_PAYMENTS) {
            List<Payment> displayedPayments = new ArrayList<>();
            List<Payment> additional = new ArrayList<>();
            for(Payment payment : payments) {
                if(displayedPayments.size() < MAX_PAYMENTS - 1) {
                    displayedPayments.add(payment);
                } else {
                    additional.add(payment);
                }
            }

            displayedPayments.add(new AdditionalPayment(additional));
            return displayedPayments;
        } else {
            return payments;
        }
    }

    private Pane getOutputsLines(List<Payment> displayedPayments) {
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
        int numOutputs = displayedPayments.size() + walletTx.getChangeMap().size() + 1;
        for(int i = 1; i <= numOutputs; i++) {
            CubicCurve curve = new CubicCurve();
            curve.getStyleClass().add("output-line");

            curve.setStartX(0);
            curve.setStartY(scale(getDiagramHeight(), 0.5, 0));
            curve.setEndX(width);
            double scaleFactor = (double)i / (numOutputs + 1);
            int nodeHeight = 20;
            double additional = (0.5 - scaleFactor) * ((double)nodeHeight);
            curve.setEndY(scale(getDiagramHeight(), scaleFactor, additional));

            curve.setControlX1(scale(width, 0.2, 0));
            curve.controlY1Property().bind(curve.startYProperty());
            curve.setControlX2(scale(width, 0.8, 0));
            curve.controlY2Property().bind(curve.endYProperty());

            group.getChildren().add(curve);
        }

        pane.getChildren().add(group);
        return pane;
    }

    private Pane getOutputsLabels(List<Payment> displayedPayments) {
        VBox outputsBox = new VBox();
        outputsBox.setMaxWidth(150);
        outputsBox.setPadding(new Insets(0, 20, 0, 10));
        outputsBox.setAlignment(Pos.CENTER_LEFT);
        outputsBox.getChildren().add(createSpacer());

        for(Payment payment : displayedPayments) {
            Glyph outputGlyph = getOutputGlyph(payment);
            boolean labelledPayment = outputGlyph.getStyleClass().stream().anyMatch(style -> List.of("premix-icon", "badbank-icon", "whirlpoolfee-icon").contains(style)) || payment instanceof AdditionalPayment;
            String recipientDesc = labelledPayment ? payment.getLabel() : payment.getAddress().toString().substring(0, 8) + "...";
            Label recipientLabel = new Label(recipientDesc, outputGlyph);
            recipientLabel.getStyleClass().add("output-label");
            recipientLabel.getStyleClass().add(labelledPayment ? "payment-label" : "recipient-label");
            Tooltip recipientTooltip = new Tooltip((walletTx.isConsolidationSend(payment) ? "Consolidate " : "Pay ") + getSatsValue(payment.getAmount()) + " sats to " + (payment instanceof AdditionalPayment ? "\n" + payment : payment.getLabel() + "\n" + payment.getAddress().toString()));
            recipientTooltip.getStyleClass().add("recipient-label");
            recipientTooltip.setShowDelay(new Duration(TOOLTIP_SHOW_DELAY));
            recipientLabel.setTooltip(recipientTooltip);
            outputsBox.getChildren().add(recipientLabel);
            outputsBox.getChildren().add(createSpacer());
        }

        for(Map.Entry<WalletNode, Long> changeEntry : walletTx.getChangeMap().entrySet()) {
            WalletNode changeNode = changeEntry.getKey();
            WalletNode defaultChangeNode = walletTx.getWallet().getFreshNode(KeyPurpose.CHANGE);
            boolean overGapLimit = (changeNode.getIndex() - defaultChangeNode.getIndex()) > walletTx.getWallet().getGapLimit();

            HBox actionBox = new HBox();
            String changeDesc = walletTx.getChangeAddress(changeNode).toString().substring(0, 8) + "...";
            Label changeLabel = new Label(changeDesc, overGapLimit ? getChangeWarningGlyph() : getChangeGlyph());
            changeLabel.getStyleClass().addAll("output-label", "change-label");
            Tooltip changeTooltip = new Tooltip("Change of " + getSatsValue(changeEntry.getValue()) + " sats to " + changeNode.getDerivationPath().replace("m", "..") + "\n" + walletTx.getChangeAddress(changeNode).toString() + (overGapLimit ? "\nAddress is beyond the gap limit!" : ""));
            changeTooltip.getStyleClass().add("change-label");
            changeTooltip.setShowDelay(new Duration(TOOLTIP_SHOW_DELAY));
            changeLabel.setTooltip(changeTooltip);

            Button nextChangeAddressButton = new Button("");
            nextChangeAddressButton.setGraphic(getChangeReplaceGlyph());
            nextChangeAddressButton.setOnAction(event -> {
                EventManager.get().post(new ReplaceChangeAddressEvent(walletTx));
            });
            Tooltip replaceChangeTooltip = new Tooltip("Use next change address");
            nextChangeAddressButton.setTooltip(replaceChangeTooltip);
            Label replaceChangeLabel = new Label("", nextChangeAddressButton);
            replaceChangeLabel.getStyleClass().add("replace-change-label");
            replaceChangeLabel.setVisible(false);
            actionBox.setOnMouseEntered(event -> replaceChangeLabel.setVisible(true));
            actionBox.setOnMouseExited(event -> replaceChangeLabel.setVisible(false));

            actionBox.getChildren().addAll(changeLabel, replaceChangeLabel);
            outputsBox.getChildren().add(actionBox);
            outputsBox.getChildren().add(createSpacer());
        }

        boolean highFee = (walletTx.getFeePercentage() > 0.1);
        Label feeLabel = highFee ? new Label("High Fee", getWarningGlyph()) : new Label("Fee", getFeeGlyph());
        feeLabel.getStyleClass().addAll("output-label", "fee-label");
        String percentage = String.format("%.2f", walletTx.getFeePercentage() * 100.0);
        Tooltip feeTooltip = new Tooltip("Fee of " + getSatsValue(walletTx.getFee()) + " sats (" + percentage + "%)");
        feeTooltip.getStyleClass().add("fee-tooltip");
        feeTooltip.setShowDelay(new Duration(TOOLTIP_SHOW_DELAY));
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
        Tooltip tooltip = new Tooltip(walletTx.getTransaction().getLength() + " bytes\n" + String.format("%.2f", walletTx.getTransaction().getVirtualSize()) + " vBytes");
        tooltip.setShowDelay(new Duration(TOOLTIP_SHOW_DELAY));
        tooltip.getStyleClass().add("transaction-tooltip");
        txLabel.setTooltip(tooltip);
        txPane.getChildren().add(txLabel);
        txPane.getChildren().add(createSpacer());

        return txPane;
    }

    public double getDiagramHeight() {
        if(AppServices.isReducedWindowHeight(this)) {
            return DIAGRAM_HEIGHT - 40;
        }

        return DIAGRAM_HEIGHT;
    }

    private Node createSpacer() {
        final Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        return spacer;
    }

    public Glyph getOutputGlyph(Payment payment) {
        if(payment.getType().equals(Payment.Type.FAKE_MIX)) {
            return getFakeMixGlyph();
        } else if(walletTx.isConsolidationSend(payment)) {
            return getConsolidationGlyph();
        } else if(walletTx.isPremixSend(payment)) {
            return getPremixGlyph();
        } else if(walletTx.isBadbankSend(payment)) {
            return getBadbankGlyph();
        } else if(payment.getType().equals(Payment.Type.WHIRLPOOL_FEE)) {
            return getWhirlpoolFeeGlyph();
        } else if(payment instanceof AdditionalPayment) {
            return ((AdditionalPayment)payment).getOutputGlyph(this);
        }

        return getPaymentGlyph();
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

    public static Glyph getPayjoinGlyph() {
        Glyph payjoinGlyph = new Glyph(FontAwesome5.FONT_NAME, FontAwesome5.Glyph.RANDOM);
        payjoinGlyph.getStyleClass().add("payjoin-icon");
        payjoinGlyph.setFontSize(12);
        return payjoinGlyph;
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

    private static class PayjoinBlockTransactionHashIndex extends BlockTransactionHashIndex {
        public PayjoinBlockTransactionHashIndex() {
            super(Sha256Hash.ZERO_HASH, 0, new Date(), 0L, 0, 0);
        }

        @Override
        public String getLabel() {
            return "Payjoin input";
        }
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

    private static class AdditionalPayment extends Payment {
        private final List<Payment> additionalPayments;

        public AdditionalPayment(List<Payment> additionalPayments) {
            super(null, additionalPayments.size() + " more...", additionalPayments.stream().map(Payment::getAmount).mapToLong(v -> v).sum(), false);
            this.additionalPayments = additionalPayments;
        }

        public Glyph getOutputGlyph(TransactionDiagram transactionDiagram) {
            Glyph glyph = null;
            for(Payment payment : additionalPayments) {
                Glyph paymentGlyph = transactionDiagram.getOutputGlyph(payment);
                if(glyph != null && !paymentGlyph.getStyleClass().equals(glyph.getStyleClass())) {
                    return getPaymentGlyph();
                }

                glyph = paymentGlyph;
            }

            return glyph;
        }

        public String toString() {
            return additionalPayments.stream().map(payment -> payment.getAddress().toString()).collect(Collectors.joining("\n"));
        }
    }
}
