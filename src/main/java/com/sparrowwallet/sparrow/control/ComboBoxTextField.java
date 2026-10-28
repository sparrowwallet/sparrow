package com.sparrowwallet.sparrow.control;

import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ComboBoxBase;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.input.Clipboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import org.controlsfx.control.textfield.CustomTextField;

import java.util.List;

public class ComboBoxTextField extends CustomTextField {
    private final ObjectProperty<ComboBox<?>> comboProperty = new SimpleObjectProperty<>();

    private boolean initialized;
    private boolean comboShowing;

    public ComboBoxTextField() {
        super();
        getStyleClass().add("combo-text-field");
        setupComboButtonField(super.rightProperty());

        disabledProperty().addListener((observable, oldValue, newValue) -> {
            if(comboProperty.isNotNull().get()) {
                comboProperty.get().setVisible(!newValue);
            }
        });

        //Down opens the list from the keyboard, as the button does with the mouse; the list takes the keys only while its combo has focus
        addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            ComboBox<?> combo = comboProperty.get();
            if(event.getCode() == KeyCode.DOWN && combo != null && combo.isVisible() && !combo.isDisabled() && !combo.isShowing()) {
                combo.requestFocus();
                combo.show();
                event.consume();
            }
        });
        comboProperty.addListener((observable, oldCombo, combo) -> {
            if(combo != null) {
                //Closing the list without a choice that moves focus elsewhere returns focus here from the combo behind this field.
                //The scene's focus owner is checked as isFocused() is false while a dialog opened by the choice is in front.
                combo.addEventHandler(ComboBoxBase.ON_HIDDEN, event -> Platform.runLater(() -> {
                    if(combo.getScene() != null && combo.getScene().getFocusOwner() == combo) {
                        requestFocus();
                    }
                }));
            }
        });
    }

    private void setupComboButtonField(ObjectProperty<Node> rightProperty) {
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

                if(!initialized) {
                    comboProperty.get().valueProperty().addListener((observable, oldValue, newValue) -> {
                        comboShowing = false;
                        //While the list is open the value follows the highlighted item, which clearing would reset
                        if(!comboProperty.get().isShowing()) {
                            Platform.runLater(() -> comboProperty.get().getSelectionModel().clearSelection());
                        }
                    });
                    //A choice made while the list is open is cleared once it closes, so the same item can be chosen again
                    comboProperty.get().addEventHandler(ComboBoxBase.ON_HIDDEN, event -> Platform.runLater(() -> comboProperty.get().getSelectionModel().clearSelection()));
                    initialized = true;
                }
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

    public ContextMenu getCustomContextMenu(List<MenuItem> customItems) {
        return new CustomContextMenu(customItems);
    }

    public class CustomContextMenu extends ContextMenu {
        public CustomContextMenu(List<MenuItem> customItems) {
            super();
            setFont(null);

            MenuItem undo = new MenuItem("Undo");
            undo.setOnAction(_ -> undo());

            MenuItem redo = new MenuItem("Redo");
            redo.setOnAction(_ -> redo());

            MenuItem cut = new MenuItem("Cut");
            cut.setOnAction(_ -> cut());

            MenuItem copy = new MenuItem("Copy");
            copy.setOnAction(_ -> copy());

            MenuItem paste = new MenuItem("Paste");
            paste.setOnAction(_ -> paste());

            MenuItem delete = new MenuItem("Delete");
            delete.setOnAction(_ -> deleteText(getSelection()));

            MenuItem selectAll = new MenuItem("Select All");
            selectAll.setOnAction(_ -> selectAll());

            getItems().addAll(undo, redo, new SeparatorMenuItem(), cut, copy, paste, delete, new SeparatorMenuItem(), selectAll);
            getItems().addAll(customItems);

            setOnShowing(_ -> {
                boolean hasSelection = getSelection().getLength() > 0;
                boolean hasText = getText() != null && !getText().isEmpty();
                boolean clipboardHasContent = Clipboard.getSystemClipboard().hasString();

                undo.setDisable(!isUndoable());
                redo.setDisable(!isRedoable());
                cut.setDisable(!isEditable() || !hasSelection);
                copy.setDisable(!hasSelection);
                paste.setDisable(!isEditable() || !clipboardHasContent);
                delete.setDisable(!hasSelection);
                selectAll.setDisable(!hasText);
            });
        }
    }
}
