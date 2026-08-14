package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.drongo.Network;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.skin.LabelSkin;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AddressLabelSkin extends LabelSkin {
    public static final int CHUNK_SIZE = 4;
    public static final Pattern CHUNK_PATTERN = Pattern.compile("(?<=\\G.{" + CHUNK_SIZE + "})");

    private final TextFlow displayFlow;
    private final ChangeListener<String> textListener;
    private final ChangeListener<Font> fontListener;

    public AddressLabelSkin(Label control) {
        super(control);

        displayFlow = new TextFlow();
        displayFlow.setManaged(false);
        displayFlow.setMouseTransparent(true);

        getChildren().addFirst(displayFlow);

        textListener = (_, _, newText) -> updateDisplay(newText);
        fontListener = (_, _, _) -> updateDisplay(control.getText());
        control.textProperty().addListener(textListener);
        control.fontProperty().addListener(fontListener);
        updateDisplay(control.getText());

        control.setStyle("-fx-text-fill: transparent;");
    }

    @Override
    public void dispose() {
        getSkinnable().textProperty().removeListener(textListener);
        getSkinnable().fontProperty().removeListener(fontListener);
        super.dispose();
    }

    private void updateDisplay(String text) {
        displayFlow.getChildren().clear();
        if(text == null || text.isEmpty()) {
            return;
        }

        List<AddressSpan> addresses = findAddresses(text);

        int pos = 0;
        for(AddressSpan span : addresses) {
            if(span.start > pos) {
                Text normalText = createText(text.substring(pos, span.start), false);
                displayFlow.getChildren().add(normalText);
            }

            addChunkedAddress(text.substring(span.start, span.end));
            pos = span.end;
        }

        if(pos < text.length()) {
            Text normalText = createText(text.substring(pos), false);
            displayFlow.getChildren().add(normalText);
        }
    }

    private void addChunkedAddress(String address) {
        String[] chunks = CHUNK_PATTERN.split(address);
        for(int i = 0; i < chunks.length; i++) {
            Text chunk = createText(chunks[i], i % 2 != 0);
            displayFlow.getChildren().add(chunk);
        }
    }

    private Text createText(String content, boolean alternate) {
        Text text = new Text(content);
        text.setFont(getSkinnable().getFont());
        text.getStyleClass().add("address-chunk");
        if(alternate) {
            text.getStyleClass().add("alternate");
        }
        return text;
    }

    private List<AddressSpan> findAddresses(String text) {
        List<AddressSpan> spans = new ArrayList<>();

        Pattern wordBoundary = Pattern.compile("\\S+");
        Matcher matcher = wordBoundary.matcher(text);

        while(matcher.find()) {
            String candidate = matcher.group();
            if(isValidAddress(candidate)) {
                spans.add(new AddressSpan(matcher.start(), matcher.end()));
            }
        }

        return spans;
    }

    private boolean isValidAddress(String candidate) {
        Network network = Network.get();
        return network.hasP2PKHAddressPrefix(candidate) || network.hasP2SHAddressPrefix(candidate) ||
                        candidate.startsWith(network.getBech32AddressHRP()) || candidate.startsWith(network.getSilentPaymentsAddressHrp());
    }

    @Override
    protected void updateChildren() {
        super.updateChildren();
        if(displayFlow != null && !getChildren().contains(displayFlow)) {
            getChildren().addFirst(displayFlow);
        }
    }

    @Override
    protected void layoutChildren(double x, double y, double w, double h) {
        super.layoutChildren(x, y, w, h);

        // Position TextFlow to align with the label's text area
        Label label = getSkinnable();
        Insets padding = label.getPadding();

        Node graphic = label.getGraphic();
        double graphicOffset = 0;
        if(graphic != null && label.getContentDisplay() == ContentDisplay.LEFT) {
            graphicOffset = graphic.getLayoutBounds().getWidth() + label.getGraphicTextGap();
        }

        displayFlow.resizeRelocate(
                x + padding.getLeft() + graphicOffset,
                y + padding.getTop(),
                w - padding.getLeft() - padding.getRight() - graphicOffset,
                h - padding.getTop() - padding.getBottom()
        );
    }

    private record AddressSpan(int start, int end) {}
}
