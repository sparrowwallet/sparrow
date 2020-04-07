package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.drongo.protocol.TransactionInput;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextFormatter;
import javafx.scene.input.InputEvent;
import javafx.util.StringConverter;

import java.time.Duration;

public class RelativeTimelockSpinner extends Spinner<Duration> {
    // Mode represents the unit that is currently being edited.
    // For convenience expose methods for incrementing and decrementing that
    // unit, and for selecting the appropriate portion in a spinner's editor
    enum Mode {
        DAYS {
            @Override
            Duration increment(Duration time, int steps) {
                return checkBounds(time, time.plusDays(steps));
            }

            @Override
            void select(RelativeTimelockSpinner spinner) {
                int index = spinner.getEditor().getText().indexOf('d');
                spinner.getEditor().selectRange(0, index);
            }
        },
        HOURS {
            @Override
            Duration increment(Duration time, int steps) {
                return checkBounds(time, time.plusHours(steps));
            }

            @Override
            void select(RelativeTimelockSpinner spinner) {
                int dayIndex = spinner.getEditor().getText().indexOf('d');
                int start = dayIndex > -1 ? dayIndex + 2 : 0;
                int hrIndex = spinner.getEditor().getText().indexOf('h');
                spinner.getEditor().selectRange(start, hrIndex);
            }
        },
        MINUTES {
            @Override
            Duration increment(Duration time, int steps) {
                return checkBounds(time, time.plusMinutes(steps*TransactionInput.RELATIVE_TIMELOCK_SECONDS_INCREMENT/60));
            }

            @Override
            void select(RelativeTimelockSpinner spinner) {
                int hrIndex = spinner.getEditor().getText().indexOf('h');
                int start = hrIndex > -1 ? hrIndex + 2 : 0;
                int minIndex = spinner.getEditor().getText().indexOf('m');
                spinner.getEditor().selectRange(start, minIndex);
            }
        },
        SECONDS {
            @Override
            Duration increment(Duration time, int steps) {
                return time.plusSeconds(steps*TransactionInput.RELATIVE_TIMELOCK_SECONDS_INCREMENT);
            }

            @Override
            void select(RelativeTimelockSpinner spinner) {
                int index = spinner.getEditor().getText().indexOf('m');
                spinner.getEditor().selectRange(index + 2, spinner.getEditor().getText().length() - 1);
            }
        };

        abstract Duration increment(Duration time, int steps);

        abstract void select(RelativeTimelockSpinner spinner);

        Duration decrement(Duration time, int steps) {
            return increment(time, -steps);
        }

        Duration checkBounds(Duration oldTime, Duration time) {
            long totalSeconds = time.getSeconds();

            if(totalSeconds < 0) {
                return Duration.ZERO;
            }

            long maxSeconds = TransactionInput.RELATIVE_TIMELOCK_VALUE_MASK * TransactionInput.RELATIVE_TIMELOCK_SECONDS_INCREMENT;
            if(totalSeconds > maxSeconds) {
                return Duration.ofSeconds(maxSeconds);
            }

            return time;
        }
    }

    // Property containing the current editing mode:

    private final ObjectProperty<Mode> mode = new SimpleObjectProperty<>(Mode.MINUTES);

    public ObjectProperty<Mode> modeProperty() {
        return mode;
    }

    public final Mode getMode() {
        return modeProperty().get();
    }

    public final void setMode(Mode mode) {
        modeProperty().set(mode);
    }

    public RelativeTimelockSpinner(Duration time) {
        setEditable(true);

        StringConverter<Duration> localTimeConverter = new StringConverter<>() {
            @Override
            public String toString(Duration time) {
                if(time.getSeconds()/86400 > 0) {
                    return time.getSeconds()/86400 + "d " + (time.getSeconds()/3600)%24 + "h";
                } else if(time.getSeconds()/3600 > 0) {
                    return (time.getSeconds()/3600)%24 + "h " + (time.getSeconds()/60)%60 + "m";
                } else {
                    return (time.getSeconds()/60)%60 + "m " + time.getSeconds()%60 + "s";
                }
            }

            @Override
            public Duration fromString(String string) {
                long totalSeconds = getTotalSecondsFromString(string);
                double rounded = Math.round((double)totalSeconds/TransactionInput.RELATIVE_TIMELOCK_SECONDS_INCREMENT);
                long roundedSeconds = (long)rounded*TransactionInput.RELATIVE_TIMELOCK_SECONDS_INCREMENT;

                Duration duration = Duration.ofSeconds(roundedSeconds);
                if(totalSeconds > 86400) {
                    if(!string.equals(this.toString(duration))) {
                        if (roundedSeconds < totalSeconds) {
                            duration = duration.plusSeconds(TransactionInput.RELATIVE_TIMELOCK_SECONDS_INCREMENT);
                        } else {
                            duration = duration.minusSeconds(TransactionInput.RELATIVE_TIMELOCK_SECONDS_INCREMENT);
                        }
                    }
                }

                long maxSeconds = TransactionInput.RELATIVE_TIMELOCK_VALUE_MASK * TransactionInput.RELATIVE_TIMELOCK_SECONDS_INCREMENT;
                if(roundedSeconds > maxSeconds) {
                    return Duration.ofSeconds(maxSeconds);
                }

                return duration;
            }

            private long getTotalSecondsFromString(String string) {
                String[] tokens = string.split(" ");
                int days = 0, hours = 0, minutes = 0, seconds = 0;
                for(int i = 0; i < tokens.length; i++) {
                    int value = Integer.parseInt(tokens[i].substring(0, tokens[i].length()-1));
                    if(tokens[i].endsWith("d")) {
                        days = value;
                    } else if(tokens[i].endsWith("h")) {
                        hours = value;
                    } else if(tokens[i].endsWith("m")) {
                        minutes = value;
                    } else if(tokens[i].endsWith("s")) {
                        seconds = value;
                    }
                }

                return ((days * 24 + hours) * 60 + minutes) * 60 + seconds;
            }
        };

        // The textFormatter both manages the text <-> LocalTime conversion,
        // and vetoes any edits that are not valid. We just make sure we have
        // two colons and only digits in between:

        TextFormatter<Duration> textFormatter = new TextFormatter<Duration>(localTimeConverter, Duration.ZERO, c -> {
            String newText = c.getControlNewText();
            if(newText.matches("[0-9]{0,4}d [0-9]{0,2}h") ||
                    newText.matches("[0-9]{0,2}h [0-9]{0,2}m") ||
                    newText.matches("[0-9]{0,2}m [0-9]{0,2}s")) {
                return c;
            }

            return null;
        });

        // The spinner value factory defines increment and decrement by
        // delegating to the current editing mode:
        SpinnerValueFactory<Duration> valueFactory = new SpinnerValueFactory<>() {
            {
                setConverter(localTimeConverter);
                setValue(time);
            }

            @Override
            public void decrement(int steps) {
                checkMode();
                setValue(mode.get().decrement(getValue(), steps));
                mode.get().select(RelativeTimelockSpinner.this);
            }

            @Override
            public void increment(int steps) {
                checkMode();
                setValue(mode.get().increment(getValue(), steps));
                mode.get().select(RelativeTimelockSpinner.this);
            }

            private void checkMode() {
                String text = RelativeTimelockSpinner.this.getEditor().getText();
                if(mode.get() == Mode.DAYS && text.indexOf('d') < 0) {
                    RelativeTimelockSpinner.this.mode.set(Mode.HOURS);
                } else if(mode.get() == Mode.HOURS && text.indexOf('h') < 0) {
                    RelativeTimelockSpinner.this.mode.set(Mode.MINUTES);
                } else if(mode.get() == Mode.MINUTES && text.indexOf('m') < 0) {
                    RelativeTimelockSpinner.this.mode.set(Mode.HOURS);
                } else if(mode.get() == Mode.SECONDS && text.indexOf('s') < 0) {
                    RelativeTimelockSpinner.this.mode.set(Mode.MINUTES);
                }
            }
        };

        this.setValueFactory(valueFactory);
        this.getEditor().setTextFormatter(textFormatter);

        // Update the mode when the user interacts with the editor.
        // This is a bit of a hack, e.g. calling spinner.getEditor().positionCaret()
        // could result in incorrect state. Directly observing the caretPostion
        // didn't work well though; getting that to work properly might be
        // a better approach in the long run.
        this.getEditor().addEventHandler(InputEvent.ANY, e -> {
            int caretPos = this.getEditor().getCaretPosition();
            int dayIndex = this.getEditor().getText().indexOf('d');
            int hrIndex = this.getEditor().getText().indexOf('h');
            int minIndex = this.getEditor().getText().indexOf('m');
            if(caretPos <= dayIndex) {
                mode.set(Mode.DAYS);
            } else if (caretPos <= hrIndex) {
                mode.set(Mode.HOURS);
            } else if (caretPos <= minIndex) {
                mode.set(Mode.MINUTES);
            } else {
                mode.set(Mode.SECONDS);
            }
        });

        // When the mode changes, select the new portion:
        mode.addListener((obs, oldMode, newMode) -> newMode.select(this));
    }

    public RelativeTimelockSpinner() {
        this(Duration.ZERO);
    }
}
