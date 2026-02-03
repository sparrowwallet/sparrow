package com.sparrowwallet.sparrow.control;

import javafx.beans.value.ChangeListener;
import javafx.geometry.Insets;
import javafx.scene.control.TreeTableCell;
import javafx.scene.control.skin.TreeTableCellSkin;
import javafx.scene.layout.Region;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

public class AddressTreeTableCellSkin<S, T> extends TreeTableCellSkin<S, T> {
    private final TextFlow displayFlow;
    private final ChangeListener<String> textListener;
    private final Text ellipsisText;
    private String currentDisplayedText;

    public AddressTreeTableCellSkin(TreeTableCell<S, T> cell) {
        super(cell);

        displayFlow = new TextFlow();
        displayFlow.setMouseTransparent(true);
        displayFlow.setMinWidth(Region.USE_PREF_SIZE);
        getChildren().add(displayFlow);

        ellipsisText = new Text("...");
        ellipsisText.fontProperty().bind(cell.fontProperty());
        ellipsisText.getStyleClass().add("address-chunk");

        textListener = (_, _, newText) -> updateDisplay(newText);
        cell.textProperty().addListener(textListener);
        updateDisplay(cell.getText());

        cell.setStyle("-fx-text-fill: transparent;");
    }

    private void updateDisplay(String text) {
        currentDisplayedText = text;
        buildDisplay(text, false);
    }

    private void buildDisplay(String text, boolean truncated) {
        displayFlow.getChildren().clear();

        if(text == null || text.isEmpty()) {
            return;
        }

        if(getSkinnable().getStyleClass().contains("address-cell")) {
            String[] chunks = AddressLabelSkin.CHUNK_PATTERN.split(text);
            for(int i = 0; i < chunks.length; i++) {
                displayFlow.getChildren().add(createText(chunks[i], i % 2 != 0));
            }
        } else {
            Text normalText = createText(text, false);
            displayFlow.getChildren().add(normalText);
        }

        if(truncated) {
            displayFlow.getChildren().add(ellipsisText);
        }
    }

    private Text createText(String content, boolean alternate) {
        Text text = new Text(content);
        text.fontProperty().bind(getSkinnable().fontProperty());
        text.getStyleClass().add("address-chunk");
        if(alternate) {
            text.getStyleClass().add("alternate");
        }
        return text;
    }

    @Override
    protected void layoutChildren(double x, double y, double w, double h) {
        super.layoutChildren(x, y, w, h);

        TreeTableCell<S, T> cell = getSkinnable();
        Insets padding = cell.getPadding();

        double leftOffset = 0;
        double topOffset = y + padding.getTop();

        Text labeledText = (Text)getChildren().stream().filter(n -> n instanceof Text).findFirst().orElse(null);
        if(labeledText != null) {
            leftOffset = labeledText.getLayoutX();
            topOffset = labeledText.getLayoutY() - labeledText.getBaselineOffset();

            String fullText = cell.getText();
            String displayedText = labeledText.getText();

            if(fullText != null && displayedText != null && !fullText.equals(displayedText)) {
                String ellipsis = cell.getEllipsisString();
                if(displayedText.endsWith(ellipsis)) {
                    String truncatedText = displayedText.substring(0, displayedText.length() - ellipsis.length());
                    if(!truncatedText.equals(currentDisplayedText)) {
                        currentDisplayedText = truncatedText;
                        buildDisplay(truncatedText, true);
                    }
                }
            } else if(fullText != null && !fullText.equals(currentDisplayedText)) {
                currentDisplayedText = fullText;
                buildDisplay(fullText, false);
            }
        }

        displayFlow.resizeRelocate(
                leftOffset,
                topOffset,
                w - padding.getLeft() - padding.getRight(),
                h - padding.getTop() - padding.getBottom()
        );
    }

    @Override
    protected void updateChildren() {
        super.updateChildren();
        if(displayFlow != null && !getChildren().contains(displayFlow)) {
            getChildren().add(displayFlow);
        }
    }

    @Override
    public void dispose() {
        getSkinnable().textProperty().removeListener(textListener);
        super.dispose();
    }
}
