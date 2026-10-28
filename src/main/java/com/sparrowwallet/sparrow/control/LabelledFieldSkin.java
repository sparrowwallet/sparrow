package com.sparrowwallet.sparrow.control;

import javafx.beans.InvalidationListener;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import org.controlsfx.control.RangeSlider;
import org.controlsfx.control.ToggleSwitch;
import tornadofx.control.Field;
import tornadofx.control.skin.FieldSkin;

import java.util.ArrayList;
import java.util.List;

/**
 * Field skin that links the field's label to its input, so screen readers announce the label with the input.
 * Applied to every field with -fx-skin in general.css.
 */
public class LabelledFieldSkin extends FieldSkin {
    private final Label label;
    private final InvalidationListener labelForListener = _ -> updateLabelFor();
    private final List<Node> visibilityWatched = new ArrayList<>();

    public LabelledFieldSkin(Field field) {
        super(field);
        label = (Label)field.getLabelContainer().getChildren().getFirst();
        field.getInputs().addListener(labelForListener);
        field.textProperty().addListener(labelForListener);
        updateLabelFor();
    }

    @Override
    public void dispose() {
        getSkinnable().getInputs().removeListener(labelForListener);
        getSkinnable().textProperty().removeListener(labelForListener);
        unwatchVisibility();
        label.setLabelFor(null);
        super.dispose();
    }

    private void updateLabelFor() {
        unwatchVisibility();
        String text = getSkinnable().getText();
        label.setLabelFor(text == null || text.isEmpty() ? null : getLabelledInput(getSkinnable().getInputs()));
    }

    private Node getLabelledInput(List<Node> inputs) {
        Node textInput = findInput(inputs, true);
        return textInput != null ? textInput : findInput(inputs, false);
    }

    //Only value controls are labelled: a button or a value shown in a plain label after the field label is not what the label names
    private Node findInput(List<Node> nodes, boolean textInputOnly) {
        for(Node node : nodes) {
            boolean input = node instanceof TextInputControl || (!textInputOnly && isValueControl(node));
            if(input || (!(node instanceof Control) && node instanceof Parent)) {
                watchVisibility(node);
            }

            if(!node.isVisible()) {
                continue;
            }

            if(input) {
                return node;
            }

            if(!(node instanceof Control) && node instanceof Parent parent) {
                Node found = findInput(parent.getChildrenUnmodifiable(), textInputOnly);
                if(found != null) {
                    return found;
                }
            }
        }

        return null;
    }

    private static boolean isValueControl(Node node) {
        return node instanceof ComboBoxBase<?> || node instanceof ChoiceBox<?> || node instanceof Spinner<?> || node instanceof Slider || node instanceof RangeSlider
                || node instanceof ToggleSwitch;
    }

    private void watchVisibility(Node node) {
        if(!visibilityWatched.contains(node)) {
            node.visibleProperty().addListener(labelForListener);
            visibilityWatched.add(node);
        }
    }

    private void unwatchVisibility() {
        visibilityWatched.forEach(node -> node.visibleProperty().removeListener(labelForListener));
        visibilityWatched.clear();
    }
}
