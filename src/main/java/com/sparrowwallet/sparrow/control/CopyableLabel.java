package com.sparrowwallet.sparrow.control;

import javafx.scene.control.TextField;

public class CopyableLabel extends TextField {
    public CopyableLabel() {
        this("");
    }

    public CopyableLabel(String text) {
        super(text);

        this.setEditable(false);
        this.getStyleClass().add("copyable-label");
        this.setPrefWidth(10);
        this.textProperty().addListener((ob, o, n) -> {
            // expand the textfield
            double width = TextUtils.computeTextWidth(this.getFont(), this.getText(), 0.0D) + 2;
            this.setPrefWidth(width);
            this.setMaxWidth(width);
        });
    }

}
