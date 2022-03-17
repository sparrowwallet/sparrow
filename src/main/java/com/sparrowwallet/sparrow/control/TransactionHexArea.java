package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.drongo.Utils;
import com.sparrowwallet.drongo.protocol.*;
import javafx.application.Platform;
import javafx.geometry.Point2D;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.IndexRange;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.stage.Popup;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.event.MouseOverTextEvent;
import org.fxmisc.richtext.model.StyleSpan;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.*;

import static org.fxmisc.richtext.model.TwoDimensional.Bias.Backward;

public class TransactionHexArea extends CodeArea {
    private static final int TRUNCATE_AT = 30000;
    private static final int SEGMENTS_INTERVAL = 250;

    private String fullHex;
    private List<TransactionSegment> previousSegmentList = new ArrayList<>();

    public TransactionHexArea() {
        super();
        addPopupHandler();
    }

    public void setTransaction(Transaction transaction) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            transaction.bitcoinSerializeToStream(baos);

            fullHex = Utils.bytesToHex(baos.toByteArray());
            String hex = fullHex;
            if(hex.length() > TRUNCATE_AT) {
                hex = hex.substring(0, TRUNCATE_AT);
                hex += "[truncated]";
            }

            clear();
            appendText(hex);
            previousSegmentList = new ArrayList<>();
            setContextMenu(new TransactionHexContextMenu(fullHex));
        } catch (IOException e) {
            throw new IllegalStateException("Can't happen");
        }
    }

    public void applyHighlighting(Transaction transaction, int selectedInputIndex, int selectedOutputIndex) {
        List<TransactionSegment> segments = getTransactionSegments(transaction, selectedInputIndex, selectedOutputIndex);
        List<TransactionSegment> changedSegments = new ArrayList<>(segments);
        changedSegments.removeAll(previousSegmentList);
        applyHighlighting(0, changedSegments);
        previousSegmentList = segments;
    }

    private void addPopupHandler() {
        Popup popup = new Popup();
        Label popupMsg = new Label();
        popupMsg.getStyleClass().add("tooltip");
        popup.getContent().add(popupMsg);

        setMouseOverTextDelay(Duration.ofMillis(150));
        addEventHandler(MouseOverTextEvent.MOUSE_OVER_TEXT_BEGIN, e -> {
            Position position = getParagraph(0).getStyleSpans().offsetToPosition(e.getCharacterIndex() + 1, Backward);
            StyleSpan<Collection<String>> styleSpan = getParagraph(0).getStyleSpans().getStyleSpan(position.getMajor());
            Point2D pos = e.getScreenPosition();
            popupMsg.setText(describeTransactionPart(styleSpan.getStyle()));
            if(!popupMsg.getText().isEmpty()) {
                popup.show(this, pos.getX(), pos.getY() + 10);
            }
        });
        addEventHandler(MouseOverTextEvent.MOUSE_OVER_TEXT_END, e -> {
            popup.hide();
        });
    }

    private void applyHighlighting(int start, List<TransactionSegment> segments) {
        int end = Math.min(segments.size(), start + SEGMENTS_INTERVAL);
        for(int i = start; i < end; i++) {
            TransactionSegment segment = segments.get(i);
            if(segment.start < TRUNCATE_AT) {
                setStyle(segment.start, Math.min(TRUNCATE_AT, segment.start + segment.length), getStyles(segment));
            }
        }

        if(end < segments.size()) {
            Platform.runLater(() -> {
                applyHighlighting(end, segments);
            });
        }
    }

    private Collection<String> getStyles(TransactionSegment segment) {
        List<String> styles = new ArrayList<>();
        styles.add(segment.style);
        if(segment.index != null) {
            styles.add("index-" + segment.index);
        }
        if(segment.witnessIndex != null) {
            styles.add("witnessindex-" + segment.witnessIndex);
        }
        return Collections.unmodifiableList(styles);
    }

    public List<TransactionSegment> getTransactionSegments(Transaction transaction, int selectedInputIndex, int selectedOutputIndex) {
        List<TransactionSegment> segments = new ArrayList<>();

        int cursor = 0;

        //Version
        cursor = addSegment(segments, cursor, 8, "version");

        if(transaction.isSegwit()) {
            //Segwit marker
            cursor = addSegment(segments, cursor, 2, "segwit-marker");
            //Segwit flag
            cursor = addSegment(segments, cursor, 2, "segwit-flag");
        }

        //Number of inputs
        VarInt numInputs = new VarInt(transaction.getInputs().size());
        cursor = addSegment(segments, cursor, numInputs.getSizeInBytes() * 2, "num-inputs");

        //Inputs
        for(int i = 0; i < transaction.getInputs().size(); i++) {
            TransactionInput input = transaction.getInputs().get(i);
            cursor = addSegment(segments, cursor, 32 * 2, i, "input-" + getIndexedStyleClass(i, selectedInputIndex, "hash"));
            cursor = addSegment(segments, cursor, 4 * 2, i, "input-" + getIndexedStyleClass(i, selectedInputIndex, "index"));
            VarInt scriptLen = new VarInt(input.getScriptBytes().length);
            cursor = addSegment(segments, cursor, scriptLen.getSizeInBytes() * 2, i, "input-" + getIndexedStyleClass(i, selectedInputIndex, "sigscript-length"));
            cursor = addSegment(segments, cursor, (int) scriptLen.value * 2, i, "input-" + getIndexedStyleClass(i, selectedInputIndex, "sigscript"));
            cursor = addSegment(segments, cursor, 4 * 2, i, "input-" + getIndexedStyleClass(i, selectedInputIndex, "sequence"));
        }

        //Number of outputs
        VarInt numOutputs = new VarInt(transaction.getOutputs().size());
        cursor = addSegment(segments, cursor, numOutputs.getSizeInBytes() * 2, "num-outputs");

        //Outputs
        for(int i = 0; i < transaction.getOutputs().size(); i++) {
            TransactionOutput output = transaction.getOutputs().get(i);
            cursor = addSegment(segments, cursor, 8 * 2, i, "output-" + getIndexedStyleClass(i, selectedOutputIndex, "value"));
            VarInt scriptLen = new VarInt(output.getScriptBytes().length);
            cursor = addSegment(segments, cursor, scriptLen.getSizeInBytes() * 2, i, "output-" + getIndexedStyleClass(i, selectedOutputIndex, "pubkeyscript-length"));
            cursor = addSegment(segments, cursor, (int) scriptLen.value * 2, i, "output-" + getIndexedStyleClass(i, selectedOutputIndex, "pubkeyscript"));
        }

        if(transaction.hasWitnesses()) {
            for (int i = 0; i < transaction.getInputs().size(); i++) {
                TransactionInput input = transaction.getInputs().get(i);
                if (input.hasWitness()) {
                    TransactionWitness witness = input.getWitness();
                    VarInt witnessCount = new VarInt(witness.getPushCount());
                    cursor = addSegment(segments, cursor, witnessCount.getSizeInBytes() * 2, i, "witness-" + getIndexedStyleClass(i, selectedInputIndex, "count"));
                    for(int j = 0; j < witness.getPushes().size(); j++) {
                        byte[] push = witness.getPushes().get(j);
                        VarInt witnessLen = new VarInt(push.length);
                        boolean isSignature = isSignature(push);
                        cursor = addSegment(segments, cursor, witnessLen.getSizeInBytes() * 2, i, j, "witness-" + getIndexedStyleClass(i, selectedInputIndex, "length"));
                        cursor = addSegment(segments, cursor, (int) witnessLen.value * 2, i, j, "witness-" + getIndexedStyleClass(i, selectedInputIndex, "data" + (isSignature ? "-signature" : "")));
                    }
                }
            }
        }

        //Locktime
        cursor = addSegment(segments, cursor, 8, "locktime");

        if(cursor != getLength()) {
            //While this is normally a good sanity check, the truncation applied means it may fail, so it is left commented out
            //throw new IllegalStateException("Cursor position does not match transaction serialisation " + cursor + ": " + getLength());
        }

        return segments;
    }

    private int addSegment(List<TransactionSegment> segments, int start, int length, String style) {
        return addSegment(segments, start, length, null, style);
    }

    private int addSegment(List<TransactionSegment> segments, int start, int length, Integer index, String style) {
        return addSegment(segments, start, length, index, null, style);
    }

    private int addSegment(List<TransactionSegment> segments, int start, int length, Integer index, Integer witnessIndex, String style) {
        segments.add(new TransactionSegment(start, length, index, witnessIndex, style));
        return start + length;
    }

    private String getIndexedStyleClass(int iterableIndex, int selectedIndex, String styleClass) {
        if (selectedIndex == -1 || selectedIndex == iterableIndex) {
            return styleClass;
        }

        return "other";
    }

    private boolean isSignature(byte[] data) {
        if(data.length >= 64) {
            try {
                TransactionSignature.decodeFromBitcoin(data, false);
                return true;
            } catch(Exception e) {
                //ignore, not a signature
            }
        }

        return false;
    }

    private String describeTransactionPart(Collection<String> styles) {
        String style = "";
        Integer index = null;
        Integer witnessIndex = null;
        Iterator<String> iter = styles.iterator();
        if(iter.hasNext()) {
            style = iter.next();
        }
        while(iter.hasNext()) {
            String indexStyle = iter.next();
            if(indexStyle.startsWith("index-")) {
                index = Integer.parseInt(indexStyle.substring("index-".length()));
            }
            if(indexStyle.startsWith("witnessindex-")) {
                witnessIndex = Integer.parseInt(indexStyle.substring("witnessindex-".length()));
            }
        }

        return switch(style) {
            case "version" -> "Transaction version";
            case "segwit-marker" -> "Segwit marker";
            case "segwit-flag" -> "Segwit flag";
            case "num-inputs" -> "Number of inputs";
            case "input-hash" -> "Input #" + index + " outpoint txid";
            case "input-index" -> "Input #" + index + " outpoint index";
            case "input-sigscript-length" -> "Input #" + index + " scriptSig length";
            case "input-sigscript" -> "Input #" + index + " scriptSig";
            case "input-sequence" -> "Input #" + index + " sequence";
            case "num-outputs" -> "Number of outputs";
            case "output-value" -> "Output #" + index + " value";
            case "output-pubkeyscript-length" -> "Output #" + index + " scriptPubKey length";
            case "output-pubkeyscript" -> "Output #" + index + " scriptPubKey";
            case "witness-count" -> "Input #" + index + " witness count";
            case "witness-length" -> "Input #" + index + " witness #" + witnessIndex + " length";
            case "witness-data", "witness-data-signature" -> "Input #" + index + " witness #" + witnessIndex + " data";
            case "locktime" -> "Locktime";
            default -> "";
        };
    }

    @Override
    public void copy() {
        IndexRange selection = getSelection();
        if(fullHex != null && selection.getLength() == getLength()) {
            ClipboardContent content = new ClipboardContent();
            content.putString(fullHex);
            Clipboard.getSystemClipboard().setContent(content);
        } else {
            super.copy();
        }
    }

    private static class TransactionSegment {
        public TransactionSegment(int start, int length, Integer index, Integer witnessIndex, String style) {
            this.start = start;
            this.length = length;
            this.index = index;
            this.witnessIndex = witnessIndex;
            this.style = style;
        }

        public int start;
        public int length;
        public Integer index;
        public Integer witnessIndex;
        public String style;

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            TransactionSegment segment = (TransactionSegment) o;
            return start == segment.start &&
                    length == segment.length &&
                    style.equals(segment.style);
        }

        @Override
        public int hashCode() {
            return Objects.hash(start, length, style);
        }
    }

    private static class TransactionHexContextMenu extends ContextMenu {
        public TransactionHexContextMenu(String hex) {
            MenuItem copy = new MenuItem("Copy All");
            copy.setOnAction(AE -> {
                hide();
                ClipboardContent content = new ClipboardContent();
                content.putString(hex);
                Clipboard.getSystemClipboard().setContent(content);
            });

            getItems().add(copy);
        }
    }
}
