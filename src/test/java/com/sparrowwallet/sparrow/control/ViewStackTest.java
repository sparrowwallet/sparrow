package com.sparrowwallet.sparrow.control;

import javafx.scene.layout.Pane;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ViewStackTest {
    @Test
    public void onlyShownViewIsVisible() {
        ViewStack viewStack = new ViewStack();
        Pane first = new Pane();
        Pane second = new Pane();

        viewStack.show(first);
        viewStack.show(second);

        Assertions.assertFalse(first.isVisible());
        Assertions.assertTrue(second.isVisible());
        Assertions.assertEquals(2, viewStack.getChildren().size());

        viewStack.show(first);

        Assertions.assertTrue(first.isVisible());
        Assertions.assertFalse(second.isVisible());
        Assertions.assertEquals(2, viewStack.getChildren().size());
    }
}
