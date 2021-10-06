package com.sparrowwallet.sparrow.control;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

public class DateLabel extends CopyableLabel {
    private static final DateFormat LAST_24_HR = new SimpleDateFormat("HH:mm");
    private static final DateFormat LAST_YEAR = new SimpleDateFormat("d MMM");
    private static final DateFormat ALL_TIME = new SimpleDateFormat("yyyy/MM/dd");

    private Date date;

    public DateLabel(Date date) {
        super(getShortDateFormat(date));
        this.date = date;
    }

    public static String getShortDateFormat(Date date) {
        if(date == null) {
            return "Unknown";
        }

        Date now = new Date();
        long elapsed = (now.getTime() - date.getTime()) / 1000;

        if(elapsed < 24 * 60 * 60) {
            return LAST_24_HR.format(date);
        } else if(elapsed < 365 * 24 * 60 * 60) {
            return LAST_YEAR.format(date);
        } else {
            return ALL_TIME.format(date);
        }
    }
}
