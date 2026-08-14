package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.drongo.Network;
import com.sparrowwallet.drongo.protocol.Base58;
import com.sparrowwallet.drongo.protocol.Bech32;
import impl.org.controlsfx.skin.CustomTextFieldSkin;
import javafx.beans.property.ObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.TextField;
import javafx.scene.layout.Region;
import javafx.scene.shape.Path;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import org.controlsfx.control.textfield.CustomTextField;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AddressTextFieldSkin extends CustomTextFieldSkin {
    private static final boolean[] BASE58_OK = buildOkTable(new String(Base58.ALPHABET));
    private static final boolean[] BECH32_DATA_OK = buildOkTable(Bech32.CHARSET);

    private final TextFlow displayFlow;
    private final Rectangle clip;
    private final ChangeListener<String> textListener;
    private final ChangeListener<Font> fontListener;

    public AddressTextFieldSkin(TextField control) {
        super(control);

        displayFlow = new TextFlow();
        displayFlow.setManaged(false);
        displayFlow.setMouseTransparent(true);

        clip = new Rectangle();
        displayFlow.setClip(clip);

        getChildren().addFirst(displayFlow);

        textListener = (_, _, newText) -> updateDisplay(newText);
        fontListener = (_, _, _) -> updateDisplay(control.getText());
        control.textProperty().addListener(textListener);
        control.fontProperty().addListener(fontListener);
        updateDisplay(control.getText());

        control.setStyle("-fx-text-fill: transparent;");

        // Unbind caret color since it's normally bound to textFill
        unbindCaretColor(getChildren());
    }

    @Override
    public void dispose() {
        getSkinnable().textProperty().removeListener(textListener);
        getSkinnable().fontProperty().removeListener(fontListener);
        super.dispose();
    }

    private void unbindCaretColor(javafx.collections.ObservableList<Node> children) {
        for(Node node : children) {
            if(node instanceof Path path && path.getStroke() != null) {
                path.fillProperty().unbind();
                path.strokeProperty().unbind();
                path.getStyleClass().add("address-field-caret");
            } else if(node instanceof javafx.scene.Parent parent) {
                unbindCaretColor(parent.getChildrenUnmodifiable());
            }
        }
    }

    @Override
    public ObjectProperty<Node> leftProperty() {
        if(getSkinnable() instanceof CustomTextField customTextField) {
            return customTextField.leftProperty();
        }

        return new SimpleObjectProperty<>();
    }

    @Override
    public ObjectProperty<Node> rightProperty() {
        if(getSkinnable() instanceof CustomTextField customTextField) {
            return customTextField.rightProperty();
        }

        return new SimpleObjectProperty<>();
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
        String[] chunks = AddressLabelSkin.CHUNK_PATTERN.split(address);
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
        if(candidate == null || candidate.isEmpty()) {
            return false;
        }

        Network network = Network.get();

        // Base58 (legacy) partial: must start with a legacy prefix and contain only base58 chars.
        if(network.hasP2PKHAddressPrefix(candidate) || network.hasP2SHAddressPrefix(candidate)) {
            return containsOnlyAscii(candidate, BASE58_OK);
        }

        String lower = candidate.toLowerCase(Locale.ROOT);

        // Bech32 (segwit v0/v1) partial: starts with HRP, then optional '1', then bech32 data charset.
        if(lower.startsWith(network.getBech32AddressHRP())) {
            return isBech32LikePartial(lower);
        }

        // Silent payments partial (bech32-like): starts with its HRP, then optional '1', then bech32 data charset.
        if(lower.startsWith(network.getSilentPaymentsAddressHrp())) {
            return isBech32LikePartial(lower);
        }

        return false;
    }

    private static boolean isBech32LikePartial(String lower) {
        int sep = lower.indexOf(Bech32.BECH32_SEPARATOR);

        if(sep < 0) {
            return containsOnlyHrpChars(lower);
        }

        String hrp = lower.substring(0, sep);
        String dataPart = lower.substring(sep + 1);

        if(hrp.isEmpty()) {
            return false;
        }

        return containsOnlyHrpChars(hrp) && containsOnlyAscii(dataPart, BECH32_DATA_OK);
    }

    private static boolean containsOnlyHrpChars(String s) {
        for(int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9');
            if(!ok) {
                return false;
            }
        }
        return true;
    }

    private static boolean[] buildOkTable(String allowed) {
        boolean[] ok = new boolean[128];
        for(int i = 0; i < allowed.length(); i++) {
            char c = allowed.charAt(i);
            if(c < ok.length) {
                ok[c] = true;
            } else {
                throw new IllegalArgumentException("Non-ASCII allowed char: " + c);
            }
        }
        return ok;
    }

    private static boolean containsOnlyAscii(String s, boolean[] ok) {
        for(int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if(c >= ok.length || !ok[c]) {
                return false;
            }
        }
        return true;
    }

    @Override
    protected void layoutChildren(double x, double y, double w, double h) {
        super.layoutChildren(x, y, w, h);

        Insets padding = getSkinnable().getPadding();

        double leftWidth = 0;
        double rightWidth = 0;
        if(getSkinnable() instanceof CustomTextField customTextField) {
            Node left = customTextField.getLeft();
            Node right = customTextField.getRight();
            if(left != null) {
                leftWidth = left.getLayoutBounds().getWidth();
                if(left instanceof Region leftRegion) {
                    leftWidth += leftRegion.getPadding().getLeft() + leftRegion.getPadding().getRight() + 1;
                }
            }
            if(right != null) {
                rightWidth = right.getLayoutBounds().getWidth();
                if(right instanceof Region rightRegion) {
                    rightWidth += rightRegion.getPadding().getLeft() + rightRegion.getPadding().getRight();
                }
            }
        }

        double availableWidth = w - padding.getLeft() - padding.getRight() - leftWidth - rightWidth;
        clip.setWidth(availableWidth);
        clip.setHeight(h);

        double topOffset = getSkinnable().getBaselineOffset() - displayFlow.getBaselineOffset();

        displayFlow.resizeRelocate(
                padding.getLeft() + leftWidth,
                topOffset,
                displayFlow.prefWidth(-1),
                h - padding.getTop() - padding.getBottom()
        );
    }

    private record AddressSpan(int start, int end) {}
}
