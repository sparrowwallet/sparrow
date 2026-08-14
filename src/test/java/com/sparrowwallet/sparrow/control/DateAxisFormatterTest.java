package com.sparrowwallet.sparrow.control;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
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
        long ts = timestamp(2024, Calendar.AUGUST, 15);
        String actual = thirdLabel(formatter, ts);
        String fourDigitYear = new SimpleDateFormat("yyyy").format(new Date(ts));
        Assertions.assertTrue(actual.contains(fourDigitYear),
                "Expected 4-digit year " + fourDigitYear + " in label, got: " + actual);
    }

    @Test
    public void subYearDurationUsesDayMonth() {
        DateAxisFormatter formatter = new DateAxisFormatter(30 * DAY);
        long ts = timestamp(2024, Calendar.AUGUST, 15);
        Assertions.assertEquals(new SimpleDateFormat("d MMM").format(new Date(ts)),
                thirdLabel(formatter, ts));
    }

    @Test
    public void subDayDurationUsesHourMinute() {
        DateAxisFormatter formatter = new DateAxisFormatter(2 * HOUR);
        long ts = timestamp(2024, Calendar.AUGUST, 15);
        Assertions.assertEquals(new SimpleDateFormat("HH:mm").format(new Date(ts)),
                thirdLabel(formatter, ts));
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
