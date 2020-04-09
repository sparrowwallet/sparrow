package com.sparrowwallet.sparrow.control;

import javafx.scene.text.Font;

public class IdLabel extends CopyableLabel {
    public IdLabel() {
        this("");
    }

    public IdLabel(String text) {
        super(text);
        setFont(Font.font("Courier"));
    }
}
