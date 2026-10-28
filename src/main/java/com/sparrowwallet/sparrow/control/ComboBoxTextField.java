package com.sparrowwallet.sparrow.control;

import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ComboBoxBase;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.ListCell;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Skin;
import javafx.scene.control.skin.ComboBoxListViewSkin;
import javafx.scene.input.Clipboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import org.controlsfx.control.textfield.CustomTextField;

import java.util.List;
import java.util.function.Consumer;

public class ComboBoxTextField extends CustomTextField {
    private final ObjectProperty<ComboBox<?>> comboProperty = new SimpleObjectProperty<>();

    private boolean initialized;
    private boolean comboShowing;
    private Object browsedItem;
    private boolean chooseBrowsedItem;

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
                    comboProperty.get().valueProperty().addListener((observable, oldValue, newValue) -> comboShowing = false);
                    initialized = true;
                }
            }
        });

        rightProperty.set(showComboButtonPane);
    }

    /**
     * Calls onChoose with the item chosen from the list. While the list is open its value follows the highlighted item, so arrow keys
     * browse and an item is only chosen by Enter, Space or a click on it; closing the list any other way chooses nothing. A value set
     * while the list is closed is chosen at once. The selection is cleared after each choice, so the same item can be chosen again.
     */
    @SuppressWarnings("unchecked")
    public <T> void setOnChoose(Consumer<T> onChoose) {
        ComboBox<T> combo = (ComboBox<T>)comboProperty.get();
        combo.valueProperty().addListener((observable, oldValue, item) -> {
            if(item != null) {
                if(combo.isShowing()) {
                    browsedItem = item;
                } else {
                    Platform.runLater(() -> combo.getSelectionModel().clearSelection());
                    onChoose.accept(item);
                }
            }
        });
        combo.addEventHandler(ComboBoxBase.ON_HIDDEN, event -> {
            T item = chooseBrowsedItem ? (T)browsedItem : null;
            browsedItem = null;
            chooseBrowsedItem = false;
            Platform.runLater(() -> combo.getSelectionModel().clearSelection());
            if(item != null) {
                onChoose.accept(item);
            }
        });
        //Arrow keys on the closed combo open the list rather than choosing the next item
        combo.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if((event.getCode() == KeyCode.UP || event.getCode() == KeyCode.DOWN) && !combo.isShowing()) {
                combo.show();
                event.consume();
            }
        });
        Consumer<Skin<?>> addChooseFilters = skin -> {
            if(skin instanceof ComboBoxListViewSkin<?> listViewSkin) {
                Node popupContent = listViewSkin.getPopupContent();
                popupContent.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
                    if(event.getCode() == KeyCode.ENTER || event.getCode() == KeyCode.SPACE) {
                        chooseBrowsedItem = true;
                    }
                });
                //Checked on the press, as the list closes in a release filter of its own that runs before one added here
                popupContent.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
                    for(Node node = event.getPickResult().getIntersectedNode(); node != null && node != popupContent; node = node.getParent()) {
                        if(node instanceof ListCell<?> cell) {
                            chooseBrowsedItem = !cell.isEmpty();
                            break;
                        }
                    }
                });
            }
        };
        if(combo.getSkin() != null) {
            addChooseFilters.accept(combo.getSkin());
        }
        combo.skinProperty().addListener((observable, oldSkin, skin) -> addChooseFilters.accept(skin));
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
