package com.sparrowwallet.sparrow.control;

import javafx.util.StringConverter;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class DateStringConverter extends StringConverter<LocalDate> {
    public static final String FORMAT_PATTERN = "yyyy/MM/dd";
    public static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern(FORMAT_PATTERN);

    @Override
    public String toString(LocalDate date) {
        if (date != null) {
            return DATE_FORMATTER.format(date);
        } else {
            return "";
        }
    }

    @Override
    public LocalDate fromString(String string) {
        if (string != null && !string.isEmpty()) {
            return LocalDate.parse(string, DATE_FORMATTER);
        } else {
            return null;
        }
    }
}
