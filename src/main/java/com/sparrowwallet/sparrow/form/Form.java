package com.sparrowwallet.sparrow.form;

import javafx.scene.Node;

import java.io.IOException;

public abstract class Form {
    public abstract Node getContents() throws IOException;
}
