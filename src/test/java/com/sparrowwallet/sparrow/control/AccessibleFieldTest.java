package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.sparrow.JavaFxTestSupport;
import javafx.fxml.FXMLLoader;
import javafx.scene.AccessibleAttribute;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Labeled;
import javafx.scene.control.TextField;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Stream;

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

    @Test
    public void paymentScreenHasNoUnnamedFocusableControls() throws Exception {
        JavaFxTestSupport.runOnFxThread(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(AccessibleFieldTest.class.getResource("/com/sparrowwallet/sparrow/wallet/payment.fxml"));
                Parent payment = loader.load();

                ComboBox<?> openWallets = (ComboBox<?>)loader.getNamespace().get("openWallets");
                Label recipientWalletLabel = (Label)loader.getNamespace().get("recipientWalletLabel");
                TextField address = (TextField)loader.getNamespace().get("address");
                AccessibleField payToField = (AccessibleField)loader.getNamespace().get("payToField");
                ComboBox<?> amountUnit = (ComboBox<?>)loader.getNamespace().get("amountUnit");
                Label amountUnitLabel = (Label)loader.getNamespace().get("amountUnitLabel");
                Label payToLabel = (Label)payToField.getLabelContainer().getChildren().getFirst();
                Assertions.assertSame(address, payToLabel.getLabelFor());
                Assertions.assertEquals("Recipient wallet:", recipientWalletLabel.getText());
                Assertions.assertSame(openWallets, recipientWalletLabel.getLabelFor());
                Assertions.assertEquals("Payment unit:", amountUnitLabel.getText());
                Assertions.assertSame(amountUnit, amountUnitLabel.getLabelFor());
                Assertions.assertFalse(recipientWalletLabel.isManaged());
                Assertions.assertFalse(recipientWalletLabel.isVisible());
                Assertions.assertFalse(amountUnitLabel.isManaged());
                Assertions.assertFalse(amountUnitLabel.isVisible());

                address.setText("bc1qrecipient");
                TextField label = (TextField)loader.getNamespace().get("label");
                label.setText("Coffee");
                TextField amount = (TextField)loader.getNamespace().get("amount");
                amount.setText("0.001");
                FiatLabel fiatAmount = (FiatLabel)loader.getNamespace().get("fiatAmount");
                fiatAmount.setText("$ 100.00");
                Assertions.assertEquals("bc1qrecipient", address.queryAccessibleAttribute(AccessibleAttribute.TEXT));
                Assertions.assertEquals("Coffee", label.queryAccessibleAttribute(AccessibleAttribute.TEXT));
                Assertions.assertEquals("0.001", amount.queryAccessibleAttribute(AccessibleAttribute.TEXT));
                Assertions.assertEquals("$ 100.00", fiatAmount.queryAccessibleAttribute(AccessibleAttribute.TEXT));

                Assertions.assertEquals(List.of(), descendants(payment)
                        .filter(Node::isFocusTraversable)
                        .filter(Node::isVisible)
                        .filter(node -> !hasAccessibleName(payment, node))
                        .map(node -> node.getClass().getSimpleName() + "#" + node.getId())
                        .toList());
            } catch(Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    public void sendScreenPreservesFeeValues() throws Exception {
        JavaFxTestSupport.runOnFxThread(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(AccessibleFieldTest.class.getResource("/com/sparrowwallet/sparrow/wallet/send.fxml"));
                loader.load();

                TextField fee = (TextField)loader.getNamespace().get("fee");
                ComboBox<?> feeAmountUnit = (ComboBox<?>)loader.getNamespace().get("feeAmountUnit");
                Label feeAmountUnitLabel = (Label)loader.getNamespace().get("feeAmountUnitLabel");
                FiatLabel fiatFeeAmount = (FiatLabel)loader.getNamespace().get("fiatFeeAmount");
                Label fiatFeeAmountLabel = (Label)loader.getNamespace().get("fiatFeeAmountLabel");
                Assertions.assertSame(feeAmountUnit, feeAmountUnitLabel.getLabelFor());
                Assertions.assertSame(fiatFeeAmount, fiatFeeAmountLabel.getLabelFor());
                Assertions.assertFalse(feeAmountUnitLabel.isManaged());
                Assertions.assertFalse(feeAmountUnitLabel.isVisible());
                Assertions.assertFalse(fiatFeeAmountLabel.isManaged());
                Assertions.assertFalse(fiatFeeAmountLabel.isVisible());

                fee.setText("1000");
                fiatFeeAmount.setText("$ 10.00");
                Assertions.assertEquals("1000", fee.queryAccessibleAttribute(AccessibleAttribute.TEXT));
                Assertions.assertEquals("$ 10.00", fiatFeeAmount.queryAccessibleAttribute(AccessibleAttribute.TEXT));
            } catch(Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    public void emptyFiatValueIsNotExposed() throws Exception {
        JavaFxTestSupport.runOnFxThread(() -> {
            FiatLabel fiatLabel = new FiatLabel();

            Assertions.assertFalse(fiatLabel.isManaged());
            Assertions.assertFalse(fiatLabel.isVisible());
            Assertions.assertFalse(fiatLabel.isFocusTraversable());

            fiatLabel.setText("$ 1.00");

            Assertions.assertTrue(fiatLabel.isManaged());
            Assertions.assertTrue(fiatLabel.isVisible());
            Assertions.assertTrue(fiatLabel.isFocusTraversable());
        });
    }

    private static Stream<Node> descendants(Parent parent) {
        return parent.getChildrenUnmodifiable().stream().flatMap(node -> node instanceof Parent child
                ? Stream.concat(Stream.of(node), descendants(child))
                : Stream.of(node));
    }

    private static boolean hasAccessibleName(Parent root, Node node) {
        if(node.getAccessibleText() != null && !node.getAccessibleText().isBlank()) {
            return true;
        }

        Object labeledBy = node.queryAccessibleAttribute(AccessibleAttribute.LABELED_BY);
        if(labeledBy instanceof Labeled label && label.getText() != null && !label.getText().isBlank()) {
            return true;
        }

        if(node instanceof Labeled labeled && labeled.getText() != null && !labeled.getText().isBlank()) {
            return true;
        }

        return descendants(root)
                .filter(Label.class::isInstance)
                .map(Label.class::cast)
                .anyMatch(label -> label.getLabelFor() == node && label.getText() != null && !label.getText().isBlank());
    }

}
