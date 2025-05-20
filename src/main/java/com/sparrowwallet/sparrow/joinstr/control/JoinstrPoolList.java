package com.sparrowwallet.sparrow.joinstr.control;

import com.sparrowwallet.sparrow.joinstr.JoinstrAction;
import com.sparrowwallet.sparrow.joinstr.JoinstrPool;

import javafx.scene.layout.*;

public class JoinstrPoolList extends VBox {

    public JoinstrPoolList(JoinstrAction action) {
        super();

        setPrefHeight(500);
        setPrefWidth(500);
        setSpacing(10);

        setMaxWidth(Double.MAX_VALUE);
        setMaxHeight(Double.MAX_VALUE);

        JoinstrPoolListRow header = new JoinstrPoolListRow();
        getChildren().add(header);

        JoinstrPool[] poolsDummy = getDummyData();

        for (JoinstrPool joinstrPool : poolsDummy) {
            JoinstrPoolListRow joinstrRow = new JoinstrPoolListRow(joinstrPool, action);
            getChildren().add(joinstrRow);
        }

    }

    private JoinstrPool[] getDummyData() {

        return new JoinstrPool[]{ new JoinstrPool("Relay1", 1234, "pubkey1", 0.001),
                               new JoinstrPool("Relay2", 1234, "pubkey2", 0.002),
                               new JoinstrPool("Relay3", 1234, "pubkey3", 0.005)};
    }

}
