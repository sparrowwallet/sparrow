package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.drongo.address.Address;
import com.sparrowwallet.drongo.address.InvalidAddressException;
import javafx.beans.value.ChangeListener;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AddressTooltipSkin implements Skin<Tooltip> {
    private final Tooltip tooltip;
    private final TextFlow textFlow;
    private final ChangeListener<String> textListener;

    public AddressTooltipSkin(Tooltip tooltip) {
        this.tooltip = tooltip;

        textFlow = new TextFlow();
        textFlow.getStyleClass().addAll(tooltip.getStyleClass());

        textListener = (_, _, newText) -> updateDisplay(newText);
        tooltip.textProperty().addListener(textListener);
        updateDisplay(tooltip.getText());
    }

    @Override
    public Tooltip getSkinnable() {
        return tooltip;
    }

    @Override
    public Node getNode() {
        return textFlow;
    }

    @Override
    public void dispose() {
        tooltip.textProperty().removeListener(textListener);
    }

    private void updateDisplay(String text) {
        textFlow.getChildren().clear();
        if(text == null || text.isEmpty()) {
            return;
        }

        List<AddressSpan> addresses = findAddresses(text);

        int pos = 0;
        for(AddressSpan span : addresses) {
            if(span.start > pos) {
                textFlow.getChildren().add(createText(text.substring(pos, span.start), false));
            }
            addChunkedAddress(text.substring(span.start, span.end));
            pos = span.end;
        }

        if(pos < text.length()) {
            textFlow.getChildren().add(createText(text.substring(pos), false));
        }
    }

    private void addChunkedAddress(String address) {
        String[] chunks = AddressLabelSkin.CHUNK_PATTERN.split(address);
        for(int i = 0; i < chunks.length; i++) {
            textFlow.getChildren().add(createText(chunks[i], i % 2 != 0));
        }
    }

    private Text createText(String content, boolean alternate) {
        Text text = new Text(content);
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
        try {
            Address.fromString(candidate);
            return true;
        } catch(InvalidAddressException e) {
            return false;
        }
    }

    private record AddressSpan(int start, int end) {}
}
