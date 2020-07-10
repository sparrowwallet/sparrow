package com.sparrowwallet.sparrow.control;

import javafx.util.StringConverter;

import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class DateAxisFormatter extends StringConverter<Number> {
    private static final DateFormat HOUR_FORMAT = new SimpleDateFormat("HH:mm");
    private static final DateFormat DAY_FORMAT = new SimpleDateFormat("d MMM");
    private static final DateFormat MONTH_FORMAT = new SimpleDateFormat("MMM yy");

    private final DateFormat dateFormat;
    private int oddCounter;

    public DateAxisFormatter(long duration) {
        if(duration < (24 * 60 * 60 * 1000L)) {
            dateFormat = HOUR_FORMAT;
        } else if(duration < (365 * 24 * 60 * 60 * 1000L)) {
            dateFormat = DAY_FORMAT;
        } else {
            dateFormat = MONTH_FORMAT;
        }
    }

    @Override
    public String toString(Number object) {
        oddCounter++;
        return oddCounter % 3 == 0 ? dateFormat.format(new Date(object.longValue())) : "";
    }

    @Override
    public Number fromString(String string) {
        try {
            Date date = dateFormat.parse(string);
            return date.getTime();
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
    }
}
