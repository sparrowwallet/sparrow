package com.sparrowwallet.sparrow;

import com.sparrowwallet.drongo.policy.PolicyType;
import com.sparrowwallet.drongo.protocol.ScriptChunk;
import com.sparrowwallet.drongo.wallet.Keystore;
import com.sparrowwallet.sparrow.control.DescriptorArea;
import com.sparrowwallet.sparrow.control.ScriptArea;
import javafx.geometry.Point2D;
import javafx.scene.control.Label;
import javafx.stage.Popup;
import org.fxmisc.richtext.event.MouseOverTextEvent;
import org.fxmisc.richtext.model.TwoDimensional;

import java.time.Duration;

import static org.fxmisc.richtext.model.TwoDimensional.Bias.Backward;

public abstract class BaseController {
    protected void initializeScriptField(ScriptArea scriptArea) {
        Popup popup = new Popup();
        Label popupMsg = new Label();
        popupMsg.getStyleClass().add("tooltip");
        popup.getContent().add(popupMsg);

        scriptArea.setMouseOverTextDelay(Duration.ofMillis(150));
        scriptArea.addEventHandler(MouseOverTextEvent.MOUSE_OVER_TEXT_BEGIN, e -> {
            TwoDimensional.Position position = scriptArea.getParagraph(0).getStyleSpans().offsetToPosition(e.getCharacterIndex(), Backward);
            if(position.getMajor() % 2 == 0 && scriptArea.getScript().getChunks().size() > position.getMajor() / 2) {
                ScriptChunk hoverChunk = scriptArea.getScript().getChunks().get(position.getMajor()/2);
                if(!hoverChunk.isOpCode()) {
                    Point2D pos = e.getScreenPosition();
                    popupMsg.setText(describeScriptChunk(hoverChunk));
                    popup.show(scriptArea, pos.getX(), pos.getY() + 10);
                }
            }
        });
        scriptArea.addEventHandler(MouseOverTextEvent.MOUSE_OVER_TEXT_END, e -> {
            popup.hide();
        });
    }

    protected String describeScriptChunk(ScriptChunk chunk) {
        return chunk.toString();
    }

    protected void initializeDescriptorField(DescriptorArea descriptorArea) {
        Popup popup = new Popup();
        Label popupMsg = new Label();
        popupMsg.getStyleClass().add("tooltip");
        popup.getContent().add(popupMsg);

        descriptorArea.setMouseOverTextDelay(Duration.ofMillis(150));
        descriptorArea.addEventHandler(MouseOverTextEvent.MOUSE_OVER_TEXT_BEGIN, e -> {
            TwoDimensional.Position position = descriptorArea.getParagraph(0).getStyleSpans().offsetToPosition(e.getCharacterIndex(), Backward);
            int index = descriptorArea.getWallet().getPolicyType() == PolicyType.SINGLE ? position.getMajor() - 1 : ((position.getMajor() - 1) / 2);
            if(position.getMajor() > 0 && index >= 0 && index < descriptorArea.getWallet().getKeystores().size()) {
                Keystore hoverKeystore = descriptorArea.getWallet().getKeystores().get(index);
                Point2D pos = e.getScreenPosition();
                popupMsg.setText(describeKeystore(hoverKeystore));
                popup.show(descriptorArea, pos.getX(), pos.getY() + 10);
            }
        });
        descriptorArea.addEventHandler(MouseOverTextEvent.MOUSE_OVER_TEXT_END, e -> {
            popup.hide();
        });
    }

    protected String describeKeystore(Keystore keystore) {
        if(keystore.isValid()) {
            StringBuilder builder = new StringBuilder();
            builder.append("[");
            builder.append(keystore.getKeyDerivation().getMasterFingerprint());
            builder.append("/");
            builder.append(keystore.getKeyDerivation().getDerivationPath().replaceFirst("^m?/", ""));
            builder.append("]");
            builder.append(keystore.getExtendedPublicKey().toString());

            return builder.toString();
        }

        return "Invalid";
    }
}
