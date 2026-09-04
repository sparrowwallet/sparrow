package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.sparrow.JavaFxTestSupport;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class AccessibleFieldTest {
    @BeforeAll
    public static void startJavaFx() throws InterruptedException {
        JavaFxTestSupport.startJavaFx();
    }

    @Test
    public void associatesLabelWithPrimaryInput() throws Exception {
        JavaFxTestSupport.runOnFxThread(() -> {
            AccessibleField field = new AccessibleField();
            ComboBox<String> comboBox = new ComboBox<>();

            field.getInputs().add(comboBox);

            Label label = (Label)field.getLabelContainer().getChildren().getFirst();
            Assertions.assertSame(comboBox, label.getLabelFor());
        });
    }

}
