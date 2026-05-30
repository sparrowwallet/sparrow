package com.sparrowwallet.sparrow.control;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.TimeZone;

public class DateAxisFormatterTest {
    private static final long HOUR = 60 * 60 * 1000L;
    private static final long DAY = 24 * HOUR;
    private static final long YEAR = 365 * DAY;

    private static String thirdLabel(DateAxisFormatter formatter, long timestamp) {
        formatter.toString(timestamp);
        formatter.toString(timestamp);
        return formatter.toString(timestamp);
    }

    private static long timestamp(int year, int month, int day) {
        Calendar cal = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
        cal.clear();
        cal.set(year, month, day);
        return cal.getTimeInMillis();
    }

    @Test
    public void multiYearDurationIncludesFourDigitYear() {
        DateAxisFormatter formatter = new DateAxisFormatter(2 * YEAR);
        String label = thirdLabel(formatter, timestamp(2024, Calendar.AUGUST, 15));
        Assertions.assertTrue(label.matches("\\p{L}+ \\d{4}"),
                "Expected month + 4-digit year, got: " + label);
        Assertions.assertTrue(label.endsWith("2024"),
                "Expected year 2024 in label, got: " + label);
    }

    @Test
    public void subYearDurationUsesDayMonth() {
        DateAxisFormatter formatter = new DateAxisFormatter(30 * DAY);
        String label = thirdLabel(formatter, timestamp(2024, Calendar.AUGUST, 15));
        Assertions.assertTrue(label.matches("\\d{1,2} \\p{L}+"),
                "Expected day + month, got: " + label);
    }

    @Test
    public void subDayDurationUsesHourMinute() {
        DateAxisFormatter formatter = new DateAxisFormatter(2 * HOUR);
        String label = thirdLabel(formatter, timestamp(2024, Calendar.AUGUST, 15));
        Assertions.assertTrue(label.matches("\\d{2}:\\d{2}"),
                "Expected HH:mm, got: " + label);
    }

    @Test
    public void everyThirdLabelIsRendered() {
        DateAxisFormatter formatter = new DateAxisFormatter(2 * YEAR);
        long ts = timestamp(2024, Calendar.AUGUST, 15);
        Assertions.assertEquals("", formatter.toString(ts));
        Assertions.assertEquals("", formatter.toString(ts));
        Assertions.assertNotEquals("", formatter.toString(ts));
    }
}
