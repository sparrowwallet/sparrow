package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.sparrow.AppController;

public class IdLabel extends CopyableLabel {
    public IdLabel() {
        this("");
    }

    public IdLabel(String text) {
        super(text);
        setFont(AppController.getMonospaceFont());
    }
}
