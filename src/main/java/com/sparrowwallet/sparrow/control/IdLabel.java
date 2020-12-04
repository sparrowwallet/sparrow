package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.sparrow.AppServices;

public class IdLabel extends CopyableLabel {
    public IdLabel() {
        this("");
    }

    public IdLabel(String text) {
        super(text);
        setFont(AppServices.getMonospaceFont());
    }
}
