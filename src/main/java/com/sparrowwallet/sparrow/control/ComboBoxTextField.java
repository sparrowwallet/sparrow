package com.sparrowwallet.sparrow.control;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import org.controlsfx.control.textfield.CustomTextField;

public class ComboBoxTextField extends CustomTextField {
    private final ObjectProperty<ComboBox<?>> comboProperty = new SimpleObjectProperty<>();

    private boolean comboShowing;

    public ComboBoxTextField() {
        super();
        getStyleClass().add("combo-text-field");
        setupCopyButtonField(super.rightProperty());
    }

    private void setupCopyButtonField(ObjectProperty<Node> rightProperty) {
        Region showComboButton = new Region();
        showComboButton.getStyleClass().addAll("graphic"); //$NON-NLS-1$
        StackPane showComboButtonPane = new StackPane(showComboButton);
        showComboButtonPane.getStyleClass().addAll("combo-button"); //$NON-NLS-1$
        showComboButtonPane.setCursor(Cursor.DEFAULT);
        showComboButtonPane.setOnMouseReleased(e -> {
            if(comboProperty.isNotNull().get()) {
                if(comboShowing) {
                    comboProperty.get().hide();
                } else {
                    comboProperty.get().show();
                }

                comboShowing = !comboShowing;
            }
        });

        rightProperty.set(showComboButtonPane);
    }

    public ComboBox<?> getComboProperty() {
        return comboProperty.get();
    }

    public ObjectProperty<ComboBox<?>> walletComboProperty() {
        return comboProperty;
    }

    public void setComboProperty(ComboBox<?> comboProperty) {
        this.comboProperty.set(comboProperty);
    }
}
