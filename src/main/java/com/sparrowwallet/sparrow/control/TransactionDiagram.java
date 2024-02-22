package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.drongo.KeyPurpose;
import com.sparrowwallet.drongo.address.Address;
import com.sparrowwallet.drongo.protocol.Sha256Hash;
import com.sparrowwallet.drongo.protocol.TransactionOutput;
import com.sparrowwallet.drongo.uri.BitcoinURI;
import com.sparrowwallet.drongo.wallet.*;
import com.sparrowwallet.sparrow.UnitFormat;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.Theme;
import com.sparrowwallet.sparrow.event.ExcludeUtxoEvent;
import com.sparrowwallet.sparrow.event.ReplaceChangeAddressEvent;
import com.sparrowwallet.sparrow.event.SorobanInitiatedEvent;
import com.sparrowwallet.sparrow.glyphfont.FontAwesome5;
import com.sparrowwallet.sparrow.glyphfont.GlyphUtils;
import com.sparrowwallet.sparrow.io.Config;
import com.sparrowwallet.sparrow.soroban.SorobanServices;
import com.sparrowwallet.sparrow.wallet.OptimizationStrategy;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.embed.swing.SwingFXUtils;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.image.Image;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.CubicCurve;
import javafx.scene.shape.Line;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;
import org.controlsfx.glyphfont.Glyph;
import org.controlsfx.tools.Platform;

import javax.imageio.ImageIO;
import java.awt.image.*;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

import static com.sparrowwallet.sparrow.glyphfont.GlyphUtils.*;

public class TransactionDiagram extends GridPane {
    private static final int MAX_UTXOS = 8;
    private static final int REDUCED_MAX_UTXOS = MAX_UTXOS - 2;
    private static final int EXPANDED_MAX_UTXOS = 20;
    private static final int MAX_PAYMENTS = 6;
    private static final int REDUCED_MAX_PAYMENTS = MAX_PAYMENTS - 2;
    private static final int EXPANDED_MAX_PAYMENTS = 18;
    private static final double DIAGRAM_HEIGHT = 210.0;
    private static final double REDUCED_DIAGRAM_HEIGHT = DIAGRAM_HEIGHT - 60;
    private static final double EXPANDED_DIAGRAM_HEIGHT = 500;
    private static final int TOOLTIP_SHOW_DELAY = 50;
    private static final int RELATIVE_SIZE_MAX_RADIUS = 7;
    private static final int ROW_HEIGHT = 27;

    private WalletTransaction walletTx;
    private final BooleanProperty finalProperty = new SimpleBooleanProperty(false);
    private final ObjectProperty<TransactionDiagramLabel> labelProperty = new SimpleObjectProperty<>(null);
    private final ObjectProperty<OptimizationStrategy> optimizationStrategyProperty = new SimpleObjectProperty<>(OptimizationStrategy.EFFICIENCY);
    private boolean expanded;
    private TransactionDiagram expandedDiagram;
    private ContextMenu contextMenu;

    private final EventHandler<MouseEvent> expandedDiagramHandler = new EventHandler<>() {
        @Override
        public void handle(MouseEvent event) {
            if(!event.isConsumed() && event.getButton() != MouseButton.SECONDARY) {
                Stage stage = new Stage(StageStyle.UNDECORATED);
                stage.setTitle(getDiagramTitle());
                stage.initOwner(TransactionDiagram.this.getScene().getWindow());
                stage.initModality(Modality.WINDOW_MODAL);
                stage.setResizable(false);

                StackPane scenePane = new StackPane();
                if(Platform.getCurrent() == Platform.WINDOWS || Platform.getCurrent() == Platform.UNIX) {
                    scenePane.setBorder(new Border(new BorderStroke(Color.DARKGRAY, BorderStrokeStyle.SOLID, CornerRadii.EMPTY, BorderWidths.DEFAULT)));
                }

                scenePane.getStylesheets().add(AppServices.class.getResource("general.css").toExternalForm());
                if(Config.get().getTheme() == Theme.DARK) {
                    scenePane.getStylesheets().add(AppServices.class.getResource("darktheme.css").toExternalForm());
                }
                scenePane.getStylesheets().add(AppServices.class.getResource("wallet/wallet.css").toExternalForm());
                scenePane.getStylesheets().add(AppServices.class.getResource("wallet/send.css").toExternalForm());

                VBox vBox = new VBox(20);
                vBox.setPadding(new Insets(20, 40, 20, 50));

                expandedDiagram = new TransactionDiagram();
                expandedDiagram.setId("transactionDiagram");
                expandedDiagram.setExpanded(true);
                expandedDiagram.setFinal(isFinal());
                updateDerivedDiagram(expandedDiagram);

                HBox buttonBox = new HBox();
                buttonBox.setAlignment(Pos.CENTER_RIGHT);
                Button button = new Button("Close");
                button.setOnAction(e -> {
                    stage.close();
                });
                buttonBox.getChildren().add(button);
                vBox.getChildren().addAll(expandedDiagram, buttonBox);
                scenePane.getChildren().add(vBox);

                Scene scene = new Scene(scenePane);
                AppServices.onEscapePressed(scene, stage::close);
                AppServices.setStageIcon(stage);
                stage.setScene(scene);
                stage.setOnShowing(e -> {
                    AppServices.moveToActiveWindowScreen(stage, 600, 460);
                });
                stage.setOnHidden(e -> {
                    expandedDiagram = null;
                });
                stage.show();
            }
        }
    };

    private final EventHandler<ContextMenuEvent> contextMenuHandler = new EventHandler<>() {
        @Override
        public void handle(ContextMenuEvent event) {
            contextMenu.show(getChildren().iterator().next(), event.getScreenX(), event.getScreenY());
        }
    };

    public void update(WalletTransaction walletTx) {
        setMinHeight(getDiagramHeight());
        setMaxHeight(getDiagramHeight());

        if(walletTx == null) {
            getChildren().clear();
        } else {
            this.walletTx = walletTx;
            update();
            setOnMouseClicked(expandedDiagramHandler);
            if(expandedDiagram != null) {
                updateDerivedDiagram(expandedDiagram);
            }
        }

        if(getLabel() != null) {
            getLabel().update(this);
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

    private void updateDerivedDiagram(TransactionDiagram diagram) {
        diagram.setOptimizationStrategy(getOptimizationStrategy());
        diagram.walletTx = walletTx;

        if(diagram.isExpanded()) {
            List<Map<BlockTransactionHashIndex, WalletNode>> utxoSets = diagram.getDisplayedUtxoSets();
            int maxSetSize = utxoSets.stream().mapToInt(Map::size).max().orElse(0);
            int maxRows = Math.max(maxSetSize * utxoSets.size(), walletTx.getPayments().size() + 2);
            double diagramHeight = Math.max(DIAGRAM_HEIGHT, Math.min(EXPANDED_DIAGRAM_HEIGHT, maxRows * ROW_HEIGHT));
            diagram.setMinHeight(diagramHeight);
            diagram.setMaxHeight(diagramHeight);
        }

        diagram.update();

        if(diagram.getScene() != null && diagram.getScene().getWindow() instanceof Stage stage) {
            stage.sizeToScene();
        }
    }

    public void update() {
        List<Map<BlockTransactionHashIndex, WalletNode>> displayedUtxoSets = getDisplayedUtxoSets();

        Pane inputsTypePane = getInputsType(displayedUtxoSets);
        GridPane.setConstraints(inputsTypePane, 0, 0);

        Pane inputsPane = getInputsLabels(displayedUtxoSets);
        GridPane.setConstraints(inputsPane, 1, 0);

        Node inputsLinesPane = getInputsLines(displayedUtxoSets);
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

        if(contextMenu == null) {
            contextMenu = new ContextMenu();
            MenuItem menuItem = new MenuItem("Save as Image...");
            menuItem.setOnAction(event -> {
                contextMenu.hide();
                saveAsImage();
            });
            contextMenu.getItems().add(menuItem);
            setOnContextMenuRequested(contextMenuHandler);
        }
    }

    private List<Map<BlockTransactionHashIndex, WalletNode>> getDisplayedUtxoSets() {
        boolean addUserSet = getOptimizationStrategy() == OptimizationStrategy.PRIVACY && SorobanServices.canWalletMix(walletTx.getWallet())
                && walletTx.getPayments().size() == 1
                && (walletTx.getPayments().get(0).getAddress().getScriptType() == walletTx.getWallet().getFreshNode(KeyPurpose.RECEIVE).getAddress().getScriptType());

        List<Map<BlockTransactionHashIndex, WalletNode>> displayedUtxoSets = new ArrayList<>();
        for(Map<BlockTransactionHashIndex, WalletNode> selectedUtxoSet : walletTx.getSelectedUtxoSets()) {
            displayedUtxoSets.add(getDisplayedUtxos(selectedUtxoSet, addUserSet ? 2 : walletTx.getSelectedUtxoSets().size()));
        }

        if(addUserSet && displayedUtxoSets.size() == 1) {
            Map<BlockTransactionHashIndex, WalletNode> addUserUtxoSet = new HashMap<>();
            addUserUtxoSet.put(new AddUserBlockTransactionHashIndex(!walletTx.isTwoPersonCoinjoin()), null);
            displayedUtxoSets.add(addUserUtxoSet);
        }

        List<Map<BlockTransactionHashIndex, WalletNode>> paddedUtxoSets = new ArrayList<>();
        int maxDisplayedSetSize = displayedUtxoSets.stream().mapToInt(Map::size).max().orElse(0);
        for(Map<BlockTransactionHashIndex, WalletNode> selectedUtxoSet : displayedUtxoSets) {
            int toAdd = maxDisplayedSetSize - selectedUtxoSet.size();
            if(toAdd > 0) {
                Map<BlockTransactionHashIndex, WalletNode> paddedUtxoSet = new LinkedHashMap<>();
                int firstAdd = toAdd / 2;
                for(int i = 0; i < firstAdd; i++) {
                    paddedUtxoSet.put(new InvisibleBlockTransactionHashIndex(i), null);
                }
                paddedUtxoSet.putAll(selectedUtxoSet);
                for(int i = firstAdd; i < toAdd; i++) {
                    paddedUtxoSet.put(new InvisibleBlockTransactionHashIndex(i), null);
                }
                paddedUtxoSets.add(paddedUtxoSet);
            } else {
                paddedUtxoSets.add(selectedUtxoSet);
            }
        }

        return paddedUtxoSets;
    }

    private Map<BlockTransactionHashIndex, WalletNode> getDisplayedUtxos(Map<BlockTransactionHashIndex, WalletNode> selectedUtxos, int numSets) {
        if(getPayjoinURI() != null && !selectedUtxos.containsValue(null)) {
            selectedUtxos = new LinkedHashMap<>(selectedUtxos);
            selectedUtxos.put(new PayjoinBlockTransactionHashIndex(), null);
        }

        int maxUtxosPerSet = getMaxUtxos() / numSets;
        if(selectedUtxos.size() > maxUtxosPerSet) {
            Map<BlockTransactionHashIndex, WalletNode> utxos = new LinkedHashMap<>();
            List<BlockTransactionHashIndex> additional = new ArrayList<>();
            for(BlockTransactionHashIndex reference : selectedUtxos.keySet()) {
                if(utxos.size() < maxUtxosPerSet - 1) {
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

    private Pane getInputsType(List<Map<BlockTransactionHashIndex, WalletNode>> displayedUtxoSets) {
        double width = 22.0;
        double height = getDiagramHeight() - 10;

        VBox allBrackets = new VBox(10);
        allBrackets.setPrefWidth(width);
        allBrackets.setPadding(new Insets(5, 0, 5, 0));
        allBrackets.setAlignment(Pos.CENTER);

        int numSets = displayedUtxoSets.size();
        if(numSets > 1) {
            double setHeight = (height / numSets) - 5;
            for(int set = 0; set < numSets; set++) {
                boolean externalUserSet = displayedUtxoSets.get(set).values().stream().anyMatch(Objects::nonNull);
                boolean addUserSet = displayedUtxoSets.get(set).keySet().stream().anyMatch(ref -> ref instanceof AddUserBlockTransactionHashIndex);
                if(externalUserSet || addUserSet) {
                    boolean replace = !isFinal() && set > 0 && SorobanServices.canWalletMix(walletTx.getWallet());
                    Glyph bracketGlyph = !replace && walletTx.isCoinControlUsed() ? getLockGlyph() : (addUserSet ? getUserAddGlyph() : getCoinsGlyph(replace));
                    String tooltipText = addUserSet ? "Click to add a mix partner" : (walletTx.getWallet().getFullDisplayName() + (replace ? "\nClick to replace with a mix partner" : ""));
                    StackPane stackPane = getBracket(width, setHeight, bracketGlyph, tooltipText);
                    allBrackets.getChildren().add(stackPane);
                } else {
                    StackPane stackPane = getBracket(width, setHeight, getUserGlyph(), "Mix partner");
                    allBrackets.getChildren().add(stackPane);
                }
            }
        } else if(walletTx.isCoinControlUsed()) {
            StackPane stackPane = getBracket(width, height, getLockGlyph(), "Coin control active");
            allBrackets.getChildren().add(stackPane);
        }

        return allBrackets;
    }

    private StackPane getBracket(double width, double height, Glyph glyph, String tooltipText) {
        StackPane stackPane = new StackPane();
        VBox pane = new VBox();

        Group group = new Group();
        VBox.setVgrow(group, Priority.ALWAYS);

        int padding = 0;
        double iconPadding = 20.0;

        Line widthLine = new Line();
        widthLine.setStartX(0);
        widthLine.setEndX(width);
        widthLine.getStyleClass().add("boundary");

        Line topYaxis = new Line();
        topYaxis.setStartX(width * 0.5);
        topYaxis.setStartY(height * 0.5 - iconPadding);
        topYaxis.setEndX(width * 0.5);
        topYaxis.setEndY(padding);
        topYaxis.getStyleClass().add("inputs-type");

        Line topBracket = new Line();
        topBracket.setStartX(width * 0.5);
        topBracket.setStartY(padding);
        topBracket.setEndX(width);
        topBracket.setEndY(padding);
        topBracket.getStyleClass().add("inputs-type");

        Line bottomYaxis = new Line();
        bottomYaxis.setStartX(width * 0.5);
        bottomYaxis.setStartY(height - padding);
        bottomYaxis.setEndX(width * 0.5);
        bottomYaxis.setEndY(height * 0.5 + iconPadding);
        bottomYaxis.getStyleClass().add("inputs-type");

        Line bottomBracket = new Line();
        bottomBracket.setStartX(width * 0.5);
        bottomBracket.setStartY(height - padding);
        bottomBracket.setEndX(width);
        bottomBracket.setEndY(height - padding);
        bottomBracket.getStyleClass().add("inputs-type");

        group.getChildren().addAll(widthLine, topYaxis, topBracket, bottomYaxis, bottomBracket);
        pane.getChildren().add(group);

        glyph.getStyleClass().add("inputs-type");
        Tooltip tooltip = new Tooltip(tooltipText);
        tooltip.getStyleClass().add("transaction-tooltip");
        tooltip.setShowDelay(new Duration(TOOLTIP_SHOW_DELAY));
        tooltip.setShowDuration(Duration.INDEFINITE);
        glyph.setTooltip(tooltip);
        stackPane.getChildren().addAll(pane, glyph);

        return stackPane;
    }

    private Pane getInputsLabels(List<Map<BlockTransactionHashIndex, WalletNode>> displayedUtxoSets) {
        VBox inputsBox = new VBox();
        inputsBox.setMaxWidth(isExpanded() ? 300 : 150);
        inputsBox.setPrefWidth(isExpanded() ? 230 : 150);
        inputsBox.setPadding(new Insets(0, 10, 0, 10));
        inputsBox.minHeightProperty().bind(minHeightProperty());
        inputsBox.setAlignment(Pos.BASELINE_RIGHT);
        inputsBox.getChildren().add(createSpacer());
        Label helper = new Label();
        double labelHeight = Math.max(TextUtils.computeTextHeight(AppServices.getMonospaceFont(), "0"), TextUtils.computeTextHeight(helper.getFont(), "0")) + 1;
        for(Map<BlockTransactionHashIndex, WalletNode> displayedUtxos : displayedUtxoSets) {
            for(BlockTransactionHashIndex input : displayedUtxos.keySet()) {
                WalletNode walletNode = displayedUtxos.get(input);
                String desc = getInputDescription(input);
                Label label = new Label(desc);
                label.setPrefHeight(labelHeight);
                label.setAlignment(Pos.BASELINE_LEFT);
                label.getStyleClass().add("utxo-label");

                Button excludeUtxoButton = new Button("");
                excludeUtxoButton.setGraphic(getExcludeGlyph());
                excludeUtxoButton.setOnAction(event -> {
                    EventManager.get().post(new ExcludeUtxoEvent(walletTx, input));
                });

                Tooltip tooltip = new Tooltip();
                Long inputValue = null;
                if(walletNode != null) {
                    inputValue = input.getValue();
                    Wallet nodeWallet = walletNode.getWallet();
                    tooltip.setText("Spending " + getSatsValue(inputValue) + " sats from " + (isFinal() ? nodeWallet.getFullDisplayName() : (nodeWallet.isNested() ? nodeWallet.getDisplayName() : "")) + " " + walletNode + "\n" +
                            input.getHashAsString() + ":" + input.getIndex() + "\n" + walletNode.getAddress());
                    tooltip.getStyleClass().add("input-label");

                    if(input.getLabel() == null || input.getLabel().isEmpty()) {
                        label.getStyleClass().add("input-label");
                    }

                    if(!isFinal()) {
                        label.setGraphic(excludeUtxoButton);
                        label.setContentDisplay(ContentDisplay.LEFT);
                    }

                    ContextMenu contextMenu = new LabelContextMenu(walletNode.getAddress(), inputValue);
                    label.setContextMenu(contextMenu);
                } else {
                    if(input instanceof PayjoinBlockTransactionHashIndex) {
                        tooltip.setText("Added once transaction is signed and sent to the payjoin server");
                    } else if(input instanceof AdditionalBlockTransactionHashIndex additionalReference) {
                        inputValue = input.getValue();
                        StringJoiner joiner = new StringJoiner("\n");
                        joiner.add("Spending " + getSatsValue(inputValue) + " sats from" + (isExpanded() ? ":" : " (click to expand):"));
                        for(BlockTransactionHashIndex additionalInput : additionalReference.getAdditionalInputs()) {
                            joiner.add(getInputDescription(additionalInput));
                        }
                        tooltip.setText(joiner.toString());
                    } else if(input instanceof InvisibleBlockTransactionHashIndex) {
                        tooltip.setText("");
                    } else if(input instanceof AddUserBlockTransactionHashIndex) {
                        tooltip.setText("");
                        label.setGraphic(walletTx.isTwoPersonCoinjoin() ? getQuestionGlyph() : getFeeWarningGlyph());
                        label.setOnMouseClicked(event -> {
                            EventManager.get().post(new SorobanInitiatedEvent(walletTx.getWallet()));
                            closeExpanded();
                            event.consume();
                        });
                    } else {
                        if(walletTx.getInputTransactions() != null && walletTx.getInputTransactions().get(input.getHash()) != null) {
                            BlockTransaction blockTransaction = walletTx.getInputTransactions().get(input.getHash());
                            TransactionOutput txOutput = blockTransaction.getTransaction().getOutputs().get((int) input.getIndex());
                            Address fromAddress = txOutput.getScript().getToAddress();
                            inputValue = txOutput.getValue();
                            tooltip.setText("Input of " + getSatsValue(inputValue) + " sats\n" + input.getHashAsString() + ":" + input.getIndex() + (fromAddress != null ? "\n" + fromAddress : ""));

                            ContextMenu contextMenu = new LabelContextMenu(fromAddress, inputValue);
                            label.setContextMenu(contextMenu);
                        } else {
                            tooltip.setText(input.getHashAsString() + ":" + input.getIndex());
                        }
                        label.getStyleClass().add("input-label");
                    }
                    if(!isFinal()) {
                        label.setGraphic(excludeUtxoButton);
                        label.setContentDisplay(ContentDisplay.LEFT);
                        excludeUtxoButton.setVisible(false);
                    }
                    tooltip.getStyleClass().add("input-label");
                }
                tooltip.setShowDelay(new Duration(TOOLTIP_SHOW_DELAY));
                tooltip.setShowDuration(Duration.INDEFINITE);
                if(!tooltip.getText().isEmpty()) {
                    label.setTooltip(tooltip);
                }

                HBox inputBox = new HBox();
                inputBox.setAlignment(Pos.CENTER_RIGHT);
                inputBox.getChildren().add(label);

                if(isExpanded()) {
                    label.setMinWidth(120);
                    Region region = new Region();
                    HBox.setHgrow(region, Priority.ALWAYS);
                    CoinLabel amountLabel = new CoinLabel();
                    if(inputValue != null) {
                        amountLabel.setValue(inputValue);
                    } else if(label.getText().trim().isEmpty()) {
                        amountLabel.setText("");
                    }
                    amountLabel.setMinWidth(TextUtils.computeTextWidth(amountLabel.getFont(), amountLabel.getText(), 0.0D) + 12);
                    amountLabel.setPadding(new Insets(0, 0, 0, 10));
                    inputBox.getChildren().addAll(region, amountLabel);
                }

                inputsBox.getChildren().add(inputBox);
                inputsBox.getChildren().add(createSpacer());
            }
        }

        return inputsBox;
    }

    private String getInputDescription(BlockTransactionHashIndex input) {
        return input.getLabel() != null && !input.getLabel().isEmpty() ? input.getLabel() : input.getHashAsString().substring(0, 8) + "..:" + input.getIndex();
    }

    String getSatsValue(long amount) {
        UnitFormat format = Config.get().getUnitFormat() == null ? UnitFormat.DOT : Config.get().getUnitFormat();
        return format.formatSatsValue(amount);
    }

    private Pane getInputsLines(List<Map<BlockTransactionHashIndex, WalletNode>> displayedUtxoSets) {
        Map<BlockTransactionHashIndex, WalletNode> displayedUtxos = new LinkedHashMap<>();
        displayedUtxoSets.forEach(displayedUtxos::putAll);

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
        long sum = walletTx.getTotal();
        int numUtxos = displayedUtxos.size();
        for(int i = 1; i <= numUtxos; i++) {
            CubicCurve curve = new CubicCurve();
            curve.getStyleClass().add("input-line");

            if(inputs.get(numUtxos-i) instanceof PayjoinBlockTransactionHashIndex || inputs.get(numUtxos-i) instanceof AddUserBlockTransactionHashIndex) {
                curve.getStyleClass().add("input-dashed-line");
            } else if(inputs.get(numUtxos-i) instanceof InvisibleBlockTransactionHashIndex) {
                continue;
            }

            curve.setStartX(RELATIVE_SIZE_MAX_RADIUS);
            double scaleFactor = (double)i / (numUtxos + 1);
            int nodeHeight = 17;
            double additional = (0.5 - scaleFactor) * ((double)nodeHeight);
            curve.setStartY(scale(getDiagramHeight(), scaleFactor, additional));
            curve.setEndX(width);
            curve.setEndY(scale(getDiagramHeight(), 0.5, 0));

            curve.setControlX1(scale(width - RELATIVE_SIZE_MAX_RADIUS, 0.2, 0));
            curve.setControlY1(curve.getStartY());
            curve.setControlX2(scale(width - RELATIVE_SIZE_MAX_RADIUS, 0.8, 0));
            curve.setControlY2(curve.getEndY());

            group.getChildren().add(curve);

            if(sum > 0 && !curve.getStyleClass().contains("input-dashed-line")) {
                long radius = Math.round((double)inputs.get(numUtxos-i).getValue() * (RELATIVE_SIZE_MAX_RADIUS - 1) / sum) + 1;
                Circle circle = new Circle(curve.getStartX(), curve.getStartY(), radius);
                circle.getStyleClass().add("size-indicator");
                group.getChildren().add(circle);
            }
        }

        pane.getChildren().add(group);
        return pane;
    }

    private static double scale(Double value, double scaleFactor, double additional) {
        return value * (1.0 - scaleFactor) + additional;
    }

    private List<Payment> getDisplayedPayments() {
        List<Payment> payments = walletTx.getPayments();

        int maxPayments = getMaxPayments();
        if(payments.size() > maxPayments) {
            List<Payment> displayedPayments = new ArrayList<>();
            List<Payment> additional = new ArrayList<>();
            for(Payment payment : payments) {
                if(displayedPayments.size() < maxPayments - 1) {
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
        long sum = walletTx.getTotal();
        List<Long> values = walletTx.getTransaction().getOutputs().stream().filter(txo -> txo.getScript().getToAddress() != null).map(TransactionOutput::getValue).collect(Collectors.toList());
        values.add(walletTx.getFee());
        int numOutputs = displayedPayments.size() + walletTx.getChangeMap().size() + 1;
        for(int i = 1; i <= numOutputs; i++) {
            CubicCurve curve = new CubicCurve();
            curve.getStyleClass().add("output-line");

            curve.setStartX(0);
            curve.setStartY(scale(getDiagramHeight(), 0.5, 0));
            curve.setEndX(width - RELATIVE_SIZE_MAX_RADIUS);
            double scaleFactor = (double)i / (numOutputs + 1);
            int nodeHeight = 20;
            double additional = (0.5 - scaleFactor) * ((double)nodeHeight);
            curve.setEndY(scale(getDiagramHeight(), scaleFactor, additional));

            curve.setControlX1(scale(width - RELATIVE_SIZE_MAX_RADIUS, 0.2, 0));
            curve.controlY1Property().bind(curve.startYProperty());
            curve.setControlX2(scale(width - RELATIVE_SIZE_MAX_RADIUS, 0.8, 0));
            curve.controlY2Property().bind(curve.endYProperty());

            group.getChildren().add(curve);

            if(sum > 0) {
                long radius = Math.min(RELATIVE_SIZE_MAX_RADIUS, Math.round((double)values.get(numOutputs-i) * (RELATIVE_SIZE_MAX_RADIUS - 1) / sum) + 1);
                Circle circle = new Circle(curve.getEndX(), curve.getEndY(), radius);
                circle.getStyleClass().add("size-indicator");
                group.getChildren().add(circle);
            }
        }

        pane.getChildren().add(group);
        return pane;
    }

    private Pane getOutputsLabels(List<Payment> displayedPayments) {
        VBox outputsBox = new VBox();
        outputsBox.setMaxWidth(isExpanded() ? 350 : 150);
        outputsBox.setPrefWidth(isExpanded() ? 230 : 150);
        outputsBox.setPadding(new Insets(0, 20, 0, 10));
        outputsBox.setAlignment(Pos.BASELINE_LEFT);
        outputsBox.getChildren().add(createSpacer());

        List<OutputNode> outputNodes = new ArrayList<>();
        for(Payment payment : displayedPayments) {
            Glyph outputGlyph = GlyphUtils.getOutputGlyph(walletTx, payment);
            boolean labelledPayment = outputGlyph.getStyleClass().stream().anyMatch(style -> List.of("premix-icon", "badbank-icon", "whirlpoolfee-icon").contains(style)) || payment instanceof AdditionalPayment;
            Label recipientLabel = new Label(payment.getLabel() == null || payment.getType() == Payment.Type.FAKE_MIX || payment.getType() == Payment.Type.MIX ? payment.getAddress().toString().substring(0, 8) + "..." : payment.getLabel(), outputGlyph);
            recipientLabel.getStyleClass().add("output-label");
            recipientLabel.getStyleClass().add(labelledPayment ? "payment-label" : "recipient-label");
            Wallet toWallet = walletTx.getToWallet(AppServices.get().getOpenWallets().keySet(), payment);
            WalletNode toNode = walletTx.getWallet() != null && !walletTx.getWallet().isBip47() ? walletTx.getAddressNodeMap().get(payment.getAddress()) : null;
            Wallet toBip47Wallet = getBip47SendWallet(payment);
            Tooltip recipientTooltip = new Tooltip((toWallet == null ? (toNode != null ? "Consolidate " : "Pay ") : "Receive ")
                    + getSatsValue(payment.getAmount()) + " sats to "
                    + (payment instanceof AdditionalPayment ? (isExpanded() ? "\n" : "(click to expand)\n") + payment : (toWallet == null ? (payment.getLabel() == null ? (toNode != null ? toNode : (toBip47Wallet == null ? "external address" : toBip47Wallet.getDisplayName())) : payment.getLabel()) : toWallet.getFullDisplayName()) + "\n" + payment.getAddress().toString())
                    + (walletTx.isDuplicateAddress(payment) ? " (Duplicate)" : ""));
            recipientTooltip.getStyleClass().add("recipient-label");
            recipientTooltip.setShowDelay(new Duration(TOOLTIP_SHOW_DELAY));
            recipientTooltip.setShowDuration(Duration.INDEFINITE);
            recipientLabel.setTooltip(recipientTooltip);
            HBox paymentBox = new HBox();
            paymentBox.setAlignment(Pos.CENTER_LEFT);
            paymentBox.getChildren().add(recipientLabel);
            if(isExpanded()) {
                recipientLabel.setMinWidth(120);
                Region region = new Region();
                region.setMinWidth(20);
                HBox.setHgrow(region, Priority.ALWAYS);
                CoinLabel amountLabel = new CoinLabel();
                amountLabel.setValue(payment.getAmount());
                amountLabel.setMinWidth(TextUtils.computeTextWidth(amountLabel.getFont(), amountLabel.getText(), 0.0D) + 2);
                paymentBox.getChildren().addAll(region, amountLabel);
            }

            outputNodes.add(new OutputNode(paymentBox, payment.getAddress(), payment.getAmount()));
        }

        Set<Integer> seenIndexes = new HashSet<>();
        for(Map.Entry<WalletNode, Long> changeEntry : walletTx.getChangeMap().entrySet()) {
            WalletNode changeNode = changeEntry.getKey();
            WalletNode defaultChangeNode = walletTx.getWallet().getFreshNode(KeyPurpose.CHANGE);
            boolean overGapLimit = (changeNode.getIndex() - defaultChangeNode.getIndex()) > walletTx.getWallet().getGapLimit();

            HBox actionBox = new HBox();
            actionBox.setAlignment(Pos.CENTER_LEFT);
            Address changeAddress = walletTx.getChangeAddress(changeNode);
            String changeDesc = changeAddress.toString().substring(0, 8) + "...";
            Label changeLabel = new Label(changeDesc, overGapLimit ? getChangeWarningGlyph() : getChangeGlyph());
            changeLabel.getStyleClass().addAll("output-label", "change-label");
            Tooltip changeTooltip = new Tooltip("Change of " + getSatsValue(changeEntry.getValue()) + " sats to " + changeNode + "\n" + walletTx.getChangeAddress(changeNode).toString() + (overGapLimit ? "\nAddress is beyond the gap limit!" : ""));
            changeTooltip.getStyleClass().add("change-label");
            changeTooltip.setShowDelay(new Duration(TOOLTIP_SHOW_DELAY));
            changeTooltip.setShowDuration(Duration.INDEFINITE);
            changeLabel.setTooltip(changeTooltip);
            actionBox.getChildren().add(changeLabel);

            if(!isFinal()) {
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
                actionBox.getChildren().add(replaceChangeLabel);
            }

            if(isExpanded()) {
                changeLabel.setMinWidth(120);
                Region region = new Region();
                region.setMinWidth(20);
                HBox.setHgrow(region, Priority.ALWAYS);
                CoinLabel amountLabel = new CoinLabel();
                amountLabel.setValue(changeEntry.getValue());
                amountLabel.setMinWidth(TextUtils.computeTextWidth(amountLabel.getFont(), amountLabel.getText(), 0.0D) + 2);
                actionBox.getChildren().addAll(region, amountLabel);
            }

            int changeIndex = outputNodes.size();
            if(isFinal()) {
                changeIndex = getOutputIndex(changeAddress, changeEntry.getValue(), seenIndexes);
                seenIndexes.add(changeIndex);
                if(changeIndex > outputNodes.size()) {
                    changeIndex = outputNodes.size();
                }
            }
            outputNodes.add(changeIndex, new OutputNode(actionBox, changeAddress, changeEntry.getValue()));
        }

        for(OutputNode outputNode : outputNodes) {
            outputsBox.getChildren().add(outputNode.outputLabel);
            outputsBox.getChildren().add(createSpacer());

            ContextMenu contextMenu = new LabelContextMenu(outputNode.address, outputNode.amount);
            if(!outputNode.outputLabel.getChildren().isEmpty() && outputNode.outputLabel.getChildren().get(0) instanceof Label outputLabelControl) {
                outputLabelControl.setContextMenu(contextMenu);
            }
        }

        boolean highFee = (walletTx.getFeePercentage() > 0.1);
        Label feeLabel = highFee ? new Label("High Fee", getFeeWarningGlyph()) : new Label("Fee", getFeeGlyph());
        feeLabel.getStyleClass().addAll("output-label", "fee-label");
        String percentage = String.format("%.2f", walletTx.getFeePercentage() * 100.0);
        Tooltip feeTooltip = new Tooltip(walletTx.getFee() < 0 ? "Unknown fee" : "Fee of " + getSatsValue(walletTx.getFee()) + " sats (" + percentage + "%)");
        feeTooltip.getStyleClass().add("fee-tooltip");
        feeTooltip.setShowDelay(new Duration(TOOLTIP_SHOW_DELAY));
        feeTooltip.setShowDuration(Duration.INDEFINITE);
        feeLabel.setTooltip(feeTooltip);

        HBox feeBox = new HBox();
        feeBox.setAlignment(Pos.CENTER_LEFT);
        feeBox.getChildren().add(feeLabel);
        if(isExpanded()) {
            Region region = new Region();
            region.setMinWidth(20);
            HBox.setHgrow(region, Priority.ALWAYS);
            CoinLabel amountLabel = new CoinLabel();
            amountLabel.setValue(walletTx.getFee());
            amountLabel.setMinWidth(TextUtils.computeTextWidth(amountLabel.getFont(), amountLabel.getText(), 0.0D) + 2);
            feeBox.getChildren().addAll(region, amountLabel);
        }

        outputsBox.getChildren().add(feeBox);
        outputsBox.getChildren().add(createSpacer());

        if(walletTx.getFee() >= 0) {
            ContextMenu contextMenu = new LabelContextMenu(null, walletTx.getFee());
            feeLabel.setContextMenu(contextMenu);
        }

        return outputsBox;
    }

    private Pane getTransactionPane() {
        VBox txPane = new VBox();
        txPane.setPadding(new Insets(0, 5, 0, 5));
        txPane.setAlignment(Pos.CENTER);
        txPane.getChildren().add(createSpacer());

        String txDesc = "Transaction";
        Label txLabel = new Label(txDesc);
        boolean isFinalized = walletTx.getTransaction().hasScriptSigs() || walletTx.getTransaction().hasWitnesses();
        Tooltip tooltip = new Tooltip(walletTx.getTransaction().getLength() + " bytes\n"
                + String.format("%.2f", walletTx.getTransaction().getVirtualSize()) + " vBytes"
                + (walletTx.getFee() < 0 ? "" : "\n" + String.format("%.2f", walletTx.getFee() / walletTx.getTransaction().getVirtualSize()) + " sats/vB" + (isFinalized ? "" : " (non-final)")));
        tooltip.setShowDelay(new Duration(TOOLTIP_SHOW_DELAY));
        tooltip.setShowDuration(Duration.INDEFINITE);
        tooltip.getStyleClass().add("transaction-tooltip");
        txLabel.setTooltip(tooltip);

        txPane.getChildren().add(txLabel);
        txPane.getChildren().add(createSpacer());

        return txPane;
    }

    private void saveAsImage() {
        Stage window = new Stage();
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Image");
        fileChooser.setInitialFileName(getDiagramTitle() + ".png");
        AppServices.moveToActiveWindowScreen(window, 800, 450);
        File file = fileChooser.showSaveDialog(window);
        if(file != null) {
            TransactionDiagram transactionDiagram = new TransactionDiagram();
            transactionDiagram.setId("transactionDiagram");
            transactionDiagram.setFinal(true);
            transactionDiagram.setExpanded(isExpanded());
            transactionDiagram.setBackground(new Background(new BackgroundFill(Color.TRANSPARENT, null, null)));
            transactionDiagram.setStyle("-fx-text-background-color: " + (Config.get().getTheme() == Theme.DARK ? "#ffffff" : "#000000"));
            updateDerivedDiagram(transactionDiagram);
            Scene scene = new Scene(transactionDiagram);
            scene.setFill(Color.TRANSPARENT);
            scene.getStylesheets().add(AppServices.class.getResource("general.css").toExternalForm());
            scene.getStylesheets().add(AppServices.class.getResource("wallet/wallet.css").toExternalForm());
            scene.getStylesheets().add(AppServices.class.getResource("wallet/send.css").toExternalForm());
            Image image = scene.snapshot(null);
            BufferedImage bufferedImage = SwingFXUtils.fromFXImage(image, null);
            try {
                ImageIO.write(bufferedImage, "png", file);
            } catch(IOException e) {
                AppServices.showErrorDialog("Error saving image", e.getMessage());
            }
        }
    }

    private String getDiagramTitle() {
        if(!isFinal() && walletTx.getPayments().size() > 0 && walletTx.getPayments().get(0).getLabel() != null) {
            return walletTx.getPayments().get(0).getLabel();
        } else {
            return "[" + walletTx.getTransaction().getTxId().toString().substring(0, 6) + "]";
        }
    }

    public double getDiagramHeight() {
        if(isExpanded()) {
            return getMaxHeight();
        }

        if(isReducedHeight()) {
            return REDUCED_DIAGRAM_HEIGHT;
        }

        return DIAGRAM_HEIGHT;
    }

    private int getMaxUtxos() {
        if(isExpanded()) {
            return EXPANDED_MAX_UTXOS;
        }

        if(isReducedHeight()) {
            return REDUCED_MAX_UTXOS;
        }

        return MAX_UTXOS;
    }

    private int getMaxPayments() {
        if(isExpanded()) {
            return EXPANDED_MAX_PAYMENTS;
        }

        if(isReducedHeight()) {
            return REDUCED_MAX_PAYMENTS;
        }

        return MAX_PAYMENTS;
    }

    private boolean isReducedHeight() {
        return !isFinal() && AppServices.isReducedWindowHeight(this);
    }

    private Node createSpacer() {
        final Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        return spacer;
    }

    private int getOutputIndex(Address address, long amount, Collection<Integer> seenIndexes) {
        List<TransactionOutput> addressOutputs = walletTx.getTransaction().getOutputs().stream().filter(txOutput -> txOutput.getScript().getToAddress() != null).collect(Collectors.toList());
        TransactionOutput output = addressOutputs.stream().filter(txOutput -> address.equals(txOutput.getScript().getToAddress()) && txOutput.getValue() == amount && !seenIndexes.contains(txOutput.getIndex())).findFirst().orElseThrow();
        return addressOutputs.indexOf(output);
    }

    private Wallet getBip47SendWallet(Payment payment) {
        if(walletTx.getWallet() != null) {
            for(Wallet childWallet : walletTx.getWallet().getChildWallets()) {
                if(childWallet.isNested()) {
                    WalletNode sendNode = childWallet.getNode(KeyPurpose.SEND);
                    for(WalletNode sendAddressNode : sendNode.getChildren()) {
                        if(sendAddressNode.getAddress().equals(payment.getAddress())) {
                            return childWallet;
                        }
                    }
                }
            }
        }

        return null;
    }

    private Glyph getUserAddGlyph() {
        Glyph userAddGlyph = new Glyph(FontAwesome5.FONT_NAME, FontAwesome5.Glyph.USER_PLUS);
        userAddGlyph.getStyleClass().add("useradd-icon");
        userAddGlyph.setFontSize(12);
        userAddGlyph.setOnMouseEntered(event -> {
            userAddGlyph.setFontSize(18);
        });
        userAddGlyph.setOnMouseExited(event -> {
            userAddGlyph.setFontSize(12);
        });
        userAddGlyph.setOnMouseClicked(event -> {
            EventManager.get().post(new SorobanInitiatedEvent(walletTx.getWallet()));
            closeExpanded();
            event.consume();
        });
        return userAddGlyph;
    }

    private Glyph getCoinsGlyph(boolean allowReplacement) {
        Glyph coinsGlyph = new Glyph(FontAwesome5.FONT_NAME, FontAwesome5.Glyph.COINS);
        coinsGlyph.setFontSize(12);
        if(allowReplacement) {
            coinsGlyph.getStyleClass().add("coins-replace-icon");
            coinsGlyph.setOnMouseEntered(event -> {
                coinsGlyph.setIcon(FontAwesome5.Glyph.USER_PLUS);
                coinsGlyph.setFontSize(18);
            });
            coinsGlyph.setOnMouseExited(event -> {
                coinsGlyph.setIcon(FontAwesome5.Glyph.COINS);
                coinsGlyph.setFontSize(12);
            });
            coinsGlyph.setOnMouseClicked(event -> {
                EventManager.get().post(new SorobanInitiatedEvent(walletTx.getWallet()));
                closeExpanded();
                event.consume();
            });
        } else {
            coinsGlyph.getStyleClass().add("coins-icon");
        }

        return coinsGlyph;
    }

    private void closeExpanded() {
        if(isExpanded() && this.getScene() != null && this.getScene().getWindow() instanceof Stage stage) {
            stage.close();
        }
    }

    public WalletTransaction getWalletTransaction() {
        return walletTx;
    }

    public boolean isFinal() {
        return finalProperty.get();
    }

    public BooleanProperty finalProperty() {
        return finalProperty;
    }

    public void setFinal(boolean isFinal) {
        this.finalProperty.set(isFinal);
    }

    public TransactionDiagramLabel getLabel() {
        return labelProperty.get();
    }

    public ObjectProperty<TransactionDiagramLabel> labelProperty() {
        return labelProperty;
    }

    public void setLabelProperty(TransactionDiagramLabel label) {
        this.labelProperty.set(label);
    }

    public OptimizationStrategy getOptimizationStrategy() {
        return optimizationStrategyProperty.get();
    }

    public ObjectProperty<OptimizationStrategy> optimizationStrategyProperty() {
        return optimizationStrategyProperty;
    }

    public void setOptimizationStrategy(OptimizationStrategy optimizationStrategy) {
        this.optimizationStrategyProperty.set(optimizationStrategy);
    }

    public boolean isExpanded() {
        return expanded;
    }

    public void setExpanded(boolean expanded) {
        this.expanded = expanded;
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
            super(Sha256Hash.ZERO_HASH, 0, new Date(), 0L, 0, additionalInputs.stream().mapToLong(BlockTransactionHashIndex::getValue).sum());
            this.additionalInputs = additionalInputs;
        }

        @Override
        public String getLabel() {
            return additionalInputs.size() + " more...";
        }

        @Override
        public long getValue() {
            return additionalInputs.stream().mapToLong(BlockTransactionHashIndex::getValue).sum();
        }

        public List<BlockTransactionHashIndex> getAdditionalInputs() {
            return additionalInputs;
        }
    }

    private static class InvisibleBlockTransactionHashIndex extends BlockTransactionHashIndex {
        public InvisibleBlockTransactionHashIndex(int index) {
            super(Sha256Hash.ZERO_HASH, 0, new Date(), 0L, index, 0);
        }

        @Override
        public String getLabel() {
            return " ";
        }
    }

    private static class AddUserBlockTransactionHashIndex extends BlockTransactionHashIndex {
        private final boolean required;

        public AddUserBlockTransactionHashIndex(boolean required) {
            super(Sha256Hash.ZERO_HASH, 0, new Date(), 0L, 0, 0);
            this.required = required;
        }

        @Override
        public String getLabel() {
            return "Add Mix Partner" + (required ? "" : "?");
        }
    }

    public static class AdditionalPayment extends Payment {
        private final List<Payment> additionalPayments;

        public AdditionalPayment(List<Payment> additionalPayments) {
            super(null, additionalPayments.size() + " more...", additionalPayments.stream().map(Payment::getAmount).mapToLong(v -> v).sum(), false);
            this.additionalPayments = additionalPayments;
        }

        public Glyph getOutputGlyph(WalletTransaction walletTx) {
            Glyph glyph = null;
            for(Payment payment : additionalPayments) {
                Glyph paymentGlyph = GlyphUtils.getOutputGlyph(walletTx, payment);
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

    private static class OutputNode {
        public Pane outputLabel;
        public Address address;
        public long amount;

        public OutputNode(Pane outputLabel, Address address, long amount) {
            this.outputLabel = outputLabel;
            this.address = address;
            this.amount = amount;
        }
    }

    private class LabelContextMenu extends ContextMenu {
        public LabelContextMenu(Address address, long value) {
            if(address != null) {
                MenuItem copyAddress = new MenuItem("Copy Address");
                copyAddress.setOnAction(event -> {
                    hide();
                    ClipboardContent content = new ClipboardContent();
                    content.putString(address.toString());
                    Clipboard.getSystemClipboard().setContent(content);
                });
                getItems().add(copyAddress);

                MenuItem showAddress = new MenuItem("Show Address as QR...");
                showAddress.setOnAction(event -> {
                    hide();
                    QRDisplayDialog qrDisplayDialog = new QRDisplayDialog(address.toString());
                    qrDisplayDialog.initOwner(TransactionDiagram.this.getScene().getWindow());
                    qrDisplayDialog.showAndWait();
                });
                getItems().add(showAddress);
            }

            MenuItem copySatsValue = new MenuItem("Copy Value in sats");
            copySatsValue.setOnAction(event -> {
                hide();
                ClipboardContent content = new ClipboardContent();
                content.putString(Long.toString(value));
                Clipboard.getSystemClipboard().setContent(content);
            });
            MenuItem copyBtcValue = new MenuItem("Copy Value in BTC");
            copyBtcValue.setOnAction(event -> {
                hide();
                ClipboardContent content = new ClipboardContent();
                UnitFormat format = Config.get().getUnitFormat() == null ? UnitFormat.DOT : Config.get().getUnitFormat();
                content.putString(format.formatBtcValue(value));
                Clipboard.getSystemClipboard().setContent(content);
            });
            getItems().addAll(copySatsValue, copyBtcValue);
        }
    }
}
