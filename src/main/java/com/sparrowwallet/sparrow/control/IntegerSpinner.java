package com.sparrowwallet.sparrow.control;

import javafx.beans.NamedArg;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.util.converter.IntegerStringConverter;

public class IntegerSpinner extends Spinner<Integer> {
    public IntegerSpinner() {
        super();
        setupEditor();
    }

    public IntegerSpinner(@NamedArg("min") int min,
                          @NamedArg("max") int max,
                          @NamedArg("initialValue") int initialValue) {
        super(min, max, initialValue);
        setupEditor();
    }

    public IntegerSpinner(@NamedArg("min") int min,
                          @NamedArg("max") int max,
                          @NamedArg("initialValue") int initialValue,
                          @NamedArg("amountToStepBy") int amountToStepBy) {
        super(min, max, initialValue, amountToStepBy);
        setupEditor();
    }

    private void setupEditor() {
        getEditor().focusedProperty().addListener((observable, oldValue, newValue) -> {
            if(newValue != null && !newValue) {
                commitValue();
            }
        });
        getEditor().textProperty().addListener((observable, oldValue, newValue) -> {
            if(!newValue.matches("\\d*")) {
                getEditor().setText(newValue.replaceAll("[^\\d]", ""));
            }
        });
    }

    public static class ValueFactory extends SpinnerValueFactory.IntegerSpinnerValueFactory {
        public ValueFactory(@NamedArg("min") int min,
                            @NamedArg("max") int max) {
            super(min, max);
            setupConverter(min);
        }

        public ValueFactory(@NamedArg("min") int min,
                            @NamedArg("max") int max,
                            @NamedArg("initialValue") int initialValue) {
            super(min, max, initialValue);
            setupConverter(initialValue);
        }

        public ValueFactory(@NamedArg("min") int min,
                            @NamedArg("max") int max,
                            @NamedArg("initialValue") int initialValue,
                            @NamedArg("amountToStepBy") int amountToStepBy) {
            super(min, max, initialValue, amountToStepBy);
            setupConverter(initialValue);
        }

        private void setupConverter(Integer defaultValue) {
            setConverter(new IntegerStringConverter() {
                @Override
                public Integer fromString(String value) {
                    if(value == null) {
                        return null;
                    }

                    value = value.trim();

                    if(value.length() < 1) {
                        return defaultValue;
                    }

                    return Integer.valueOf(value);
                }
            });
        }
    }
}
