package com.sparrowwallet.sparrow.transaction;

import javafx.scene.Node;

import java.io.IOException;

public abstract class TransactionForm {
    public abstract Node getContents() throws IOException;
}
