package com.sparrowwallet.sparrow.control;

import impl.org.controlsfx.skin.CustomTextFieldSkin;
import javafx.animation.FadeTransition;
import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.scene.Cursor;
import javafx.scene.control.PasswordField;
import javafx.scene.layout.*;
import javafx.util.Duration;
import org.controlsfx.control.textfield.CustomPasswordField;

public abstract class ViewPasswordFieldSkin extends CustomTextFieldSkin {
    private static final Duration FADE_DURATION = Duration.millis(350);
    public static final char BULLET = '\u25CF';

    private boolean mask = true;

    public ViewPasswordFieldSkin(CustomPasswordField textField) {
        super(textField);

        Region viewPasswordButton = new Region();
        viewPasswordButton.getStyleClass().addAll("graphic");
        StackPane viewPasswordButtonPane = new StackPane(viewPasswordButton);
        viewPasswordButtonPane.getStyleClass().addAll("view-password-button");
        viewPasswordButtonPane.setOpacity(0.0);
        viewPasswordButtonPane.setCursor(Cursor.DEFAULT);

        viewPasswordButtonPane.setOnMouseReleased(e -> {
            if(mask) {
                viewPasswordButtonPane.getStyleClass().remove("view-password-button");
                viewPasswordButtonPane.getStyleClass().addAll("hide-password-button");
                mask = false;
            } else {
                viewPasswordButtonPane.getStyleClass().remove("hide-password-button");
                viewPasswordButtonPane.getStyleClass().addAll("view-password-button");
                mask = true;
            }
            textField.setText(textField.getText());
            textField.end();
        });

        textField.rightProperty().set(viewPasswordButtonPane);

        final FadeTransition fader = new FadeTransition(FADE_DURATION, viewPasswordButtonPane);
        fader.setCycleCount(1);

        textField.textProperty().addListener(new InvalidationListener() {
            @Override
            public void invalidated(Observable arg0) {
                String text = textField.getText();
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

    @Override
    protected String maskText(String txt) {
        if(getSkinnable() instanceof PasswordField && mask) {
            int n = txt.length();
            StringBuilder passwordBuilder = new StringBuilder(n);
            for (int i = 0; i < n; i++) {
                passwordBuilder.append(BULLET);
            }
            return passwordBuilder.toString();
        } else {
            return txt;
        }
    }
}
