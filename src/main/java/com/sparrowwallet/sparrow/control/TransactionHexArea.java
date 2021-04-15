package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.drongo.Utils;
import com.sparrowwallet.drongo.protocol.*;
import javafx.application.Platform;
import javafx.geometry.Point2D;
import javafx.scene.control.Label;
import javafx.stage.Popup;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.event.MouseOverTextEvent;
import org.fxmisc.richtext.model.StyleSpan;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import static org.fxmisc.richtext.model.TwoDimensional.Bias.Backward;

public class TransactionHexArea extends CodeArea {
    private static final int TRUNCATE_AT = 30000;
    private static final int SEGMENTS_INTERVAL = 250;

    private List<TransactionSegment> previousSegmentList = new ArrayList<>();

    public TransactionHexArea() {
        super();
        addPopupHandler();
    }

    public void setTransaction(Transaction transaction) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            transaction.bitcoinSerializeToStream(baos);

            String hex = Utils.bytesToHex(baos.toByteArray());
            if(hex.length() > TRUNCATE_AT) {
                hex = hex.substring(0, TRUNCATE_AT);
                hex += "[truncated]";
            }

            clear();
            appendText(hex);
            previousSegmentList = new ArrayList<>();
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
                setStyleClass(segment.start, Math.min(TRUNCATE_AT, segment.start + segment.length), segment.style);
            }
        }

        if(end < segments.size()) {
            Platform.runLater(() -> {
                applyHighlighting(end, segments);
            });
        }
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
            cursor = addSegment(segments, cursor, 32 * 2, "input-" + getIndexedStyleClass(i, selectedInputIndex, "hash"));
            cursor = addSegment(segments, cursor, 4 * 2, "input-" + getIndexedStyleClass(i, selectedInputIndex, "index"));
            VarInt scriptLen = new VarInt(input.getScriptBytes().length);
            cursor = addSegment(segments, cursor, scriptLen.getSizeInBytes() * 2, "input-" + getIndexedStyleClass(i, selectedInputIndex, "sigscript-length"));
            cursor = addSegment(segments, cursor, (int) scriptLen.value * 2, "input-" + getIndexedStyleClass(i, selectedInputIndex, "sigscript"));
            cursor = addSegment(segments, cursor, 4 * 2, "input-" + getIndexedStyleClass(i, selectedInputIndex, "sequence"));
        }

        //Number of outputs
        VarInt numOutputs = new VarInt(transaction.getOutputs().size());
        cursor = addSegment(segments, cursor, numOutputs.getSizeInBytes() * 2, "num-outputs");

        //Outputs
        for(int i = 0; i < transaction.getOutputs().size(); i++) {
            TransactionOutput output = transaction.getOutputs().get(i);
            cursor = addSegment(segments, cursor, 8 * 2, "output-" + getIndexedStyleClass(i, selectedOutputIndex, "value"));
            VarInt scriptLen = new VarInt(output.getScriptBytes().length);
            cursor = addSegment(segments, cursor, scriptLen.getSizeInBytes() * 2, "output-" + getIndexedStyleClass(i, selectedOutputIndex, "pubkeyscript-length"));
            cursor = addSegment(segments, cursor, (int) scriptLen.value * 2, "output-" + getIndexedStyleClass(i, selectedOutputIndex, "pubkeyscript"));
        }

        if(transaction.hasWitnesses()) {
            for (int i = 0; i < transaction.getInputs().size(); i++) {
                TransactionInput input = transaction.getInputs().get(i);
                if (input.hasWitness()) {
                    TransactionWitness witness = input.getWitness();
                    VarInt witnessCount = new VarInt(witness.getPushCount());
                    cursor = addSegment(segments, cursor, witnessCount.getSizeInBytes() * 2, "witness-" + getIndexedStyleClass(i, selectedInputIndex, "count"));
                    for (byte[] push : witness.getPushes()) {
                        VarInt witnessLen = new VarInt(push.length);
                        cursor = addSegment(segments, cursor, witnessLen.getSizeInBytes() * 2, "witness-" + getIndexedStyleClass(i, selectedInputIndex, "length"));
                        cursor = addSegment(segments, cursor, (int) witnessLen.value * 2, "witness-" + getIndexedStyleClass(i, selectedInputIndex, "data"));
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
        segments.add(new TransactionSegment(start, length, style));
        return start + length;
    }

    private String getIndexedStyleClass(int iterableIndex, int selectedIndex, String styleClass) {
        if (selectedIndex == -1 || selectedIndex == iterableIndex) {
            return styleClass;
        }

        return "other";
    }

    private String describeTransactionPart(Collection<String> styles) {
        String style = styles.isEmpty() ? "" : styles.iterator().next();
        return switch(style) {
            case "version" -> "Transaction version";
            case "segwit-marker" -> "Segwit marker";
            case "segwit-flag" -> "Segwit flag";
            case "num-inputs" -> "Number of inputs";
            case "input-hash" -> "Input transaction ID";
            case "input-index" -> "Input transaction index";
            case "input-sigscript-length" -> "ScriptSig length";
            case "input-sigscript" -> "ScriptSig";
            case "input-sequence" -> "Sequence";
            case "num-outputs" -> "Number of outputs";
            case "output-value" -> "Output value";
            case "output-pubkeyscript-length" -> "ScriptPubKey length";
            case "output-pubkeyscript" -> "ScriptPubKey";
            case "witness-count" -> "Witness count";
            case "witness-length" -> "Witness length";
            case "witness-data" -> "Witness data";
            case "locktime" -> "Locktime";
            default -> "";
        };
    }

    private static class TransactionSegment {
        public TransactionSegment(int start, int length, String style) {
            this.start = start;
            this.length = length;
            this.style = style;
        }

        public int start;
        public int length;
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
}
