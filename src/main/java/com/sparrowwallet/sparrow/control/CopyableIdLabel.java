package com.sparrowwallet.sparrow.control;

import javafx.scene.text.Font;

import java.awt.*;

public class CopyableIdLabel extends CopyableLabel {
    public CopyableIdLabel() {
        this("");
    }

    public CopyableIdLabel(String text) {
        super(text);
        setFont(Font.font("Courier"));
    }
}
