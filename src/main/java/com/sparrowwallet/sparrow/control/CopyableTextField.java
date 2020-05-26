package com.sparrowwallet.sparrow.control;

import javafx.animation.FadeTransition;
import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.beans.property.ObjectProperty;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;
import org.controlsfx.control.textfield.CustomTextField;

public class CopyableTextField extends CustomTextField {
    private static final Duration FADE_DURATION = Duration.millis(350);

    public CopyableTextField() {
        super();
        getStyleClass().add("copyable-text-field");
        setupCopyButtonField(super.rightProperty());
    }

    private void setupCopyButtonField(ObjectProperty<Node> rightProperty) {
        Region copyButton = new Region();
        copyButton.getStyleClass().addAll("graphic"); //$NON-NLS-1$
        StackPane copyButtonPane = new StackPane(copyButton);
        copyButtonPane.getStyleClass().addAll("copy-button"); //$NON-NLS-1$
        copyButtonPane.setOpacity(0.0);
        copyButtonPane.setCursor(Cursor.DEFAULT);
        copyButtonPane.setOnMouseReleased(e -> {
            ClipboardContent content = new ClipboardContent();
            content.putString(getText());
            Clipboard.getSystemClipboard().setContent(content);
        });

        rightProperty.set(copyButtonPane);

        final FadeTransition fader = new FadeTransition(FADE_DURATION, copyButtonPane);
        fader.setCycleCount(1);

        textProperty().addListener(new InvalidationListener() {
            @Override
            public void invalidated(Observable arg0) {
                String text = getText();
                boolean isTextEmpty = text == null || text.isEmpty();
                boolean isButtonVisible = fader.getNode().getOpacity() > 0;

                if (isTextEmpty && isButtonVisible) {
                    setButtonVisible(false);
                } else if (!isTextEmpty && !isButtonVisible) {
                    setButtonVisible(true);
                }
            }

            private void setButtonVisible( boolean visible ) {
                fader.setFromValue(visible? 0.0: 1.0);
                fader.setToValue(visible? 1.0: 0.0);
                fader.play();
            }
        });
    }
}
