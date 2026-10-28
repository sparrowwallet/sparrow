package com.sparrowwallet.sparrow.control;

import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.collections.FXCollections;
import javafx.event.EventHandler;
import javafx.scene.AccessibleAction;
import javafx.scene.AccessibleAttribute;
import javafx.scene.AccessibleRole;
import javafx.scene.Node;
import javafx.scene.TraversalDirection;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import org.controlsfx.control.RangeSlider;

/**
 * Range slider for the multisig threshold (low thumb) and number of cosigners (high thumb), exposed to screen readers as a slider
 * for whichever thumb is selected. The thumbs are not controls, so without this the slider has no role, value or name.
 */
public class CosignersSlider extends RangeSlider {
    private final InvalidationListener accessibleValueListener = _ -> updateAccessibleValue();
    private final EventHandler<KeyEvent> sceneKeyPressedFilter = event -> shiftTabPressed = event.getCode() == KeyCode.TAB && event.isShiftDown();
    private final EventHandler<KeyEvent> sceneKeyReleasedFilter = _ -> shiftTabPressed = false;
    private boolean shiftTabPressed;
    private Node highThumb;

    public CosignersSlider() {
        setAccessibleRole(AccessibleRole.SLIDER);
        lowValueProperty().addListener(accessibleValueListener);
        highValueProperty().addListener(accessibleValueListener);
        skinProperty().addListener((_, _, _) -> {
            if(highThumb != null) {
                highThumb.focusedProperty().removeListener(accessibleValueListener);
            }
            //Tab moves between the thumbs by setting their focused state, while the slider itself keeps the scene focus.
            //A thumb taking the scene focus is a further, unnamed Tab stop.
            Node lowThumb = lookup(".low-thumb");
            if(lowThumb != null) {
                lowThumb.setFocusTraversable(false);
            }
            highThumb = lookup(".high-thumb");
            if(highThumb != null) {
                highThumb.setFocusTraversable(false);
                highThumb.focusedProperty().addListener(accessibleValueListener);
            }
        });
        //Leaving the slider by Tab uses scene traversal: the skin otherwise focuses the next sibling or parent, an unnamed Tab stop
        addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            boolean leaving = event.isShiftDown() ? !isHighThumbSelected() : isHighThumbSelected();
            if(event.getCode() == KeyCode.TAB && !event.isShortcutDown() && !event.isControlDown() && !event.isAltDown() && leaving) {
                requestFocusTraversal(event.isShiftDown() ? TraversalDirection.PREVIOUS : TraversalDirection.NEXT);
                event.consume();
            }
        });
        //The skin selects the low thumb whenever the slider gains focus, so arriving backwards selects the high thumb with its own Tab handling.
        //Focus moves on the key press, so the Shift-Tab is only noted until its release.
        sceneProperty().addListener((_, oldScene, newScene) -> {
            if(oldScene != null) {
                oldScene.removeEventFilter(KeyEvent.KEY_PRESSED, sceneKeyPressedFilter);
                oldScene.removeEventFilter(KeyEvent.KEY_RELEASED, sceneKeyReleasedFilter);
            }
            if(newScene != null) {
                newScene.addEventFilter(KeyEvent.KEY_PRESSED, sceneKeyPressedFilter);
                newScene.addEventFilter(KeyEvent.KEY_RELEASED, sceneKeyReleasedFilter);
            }
        });
        focusedProperty().addListener((_, _, focused) -> {
            if(focused && shiftTabPressed) {
                Platform.runLater(() -> {
                    if(isFocused() && !isHighThumbSelected()) {
                        fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.TAB, false, false, false, false));
                    }
                });
            }
        });
        updateAccessibleValue();
    }

    private boolean isHighThumbSelected() {
        return highThumb != null && highThumb.isFocused();
    }

    private void updateAccessibleValue() {
        //Screen readers announce a slider's value as a percentage of its range, so the counts are given in the name
        int required = (int)getLowValue();
        int cosigners = (int)getHighValue();
        setAccessibleText(isHighThumbSelected() ? cosigners + (cosigners == 1 ? " cosigner, " : " cosigners, ") + required + " required to sign" : required + " of " + cosigners + " required to sign");
        notifyAccessibleAttributeChanged(AccessibleAttribute.VALUE);
    }

    @Override
    public Object queryAccessibleAttribute(AccessibleAttribute attribute, Object... parameters) {
        return switch(attribute) {
            case VALUE -> isHighThumbSelected() ? getHighValue() : getLowValue();
            case MIN_VALUE -> getMin();
            case MAX_VALUE -> getMax();
            case ORIENTATION -> getOrientation();
            //The tick labels would otherwise be read as separate numbers
            case CHILDREN -> FXCollections.emptyObservableList();
            default -> super.queryAccessibleAttribute(attribute, parameters);
        };
    }

    @Override
    public void executeAccessibleAction(AccessibleAction action, Object... parameters) {
        switch(action) {
            case INCREMENT -> adjustSelected(getBlockIncrement());
            case DECREMENT -> adjustSelected(-getBlockIncrement());
            default -> super.executeAccessibleAction(action, parameters);
        }
    }

    private void adjustSelected(double delta) {
        if(isHighThumbSelected()) {
            adjustHighValue(getHighValue() + delta);
        } else {
            adjustLowValue(getLowValue() + delta);
        }
    }
}
