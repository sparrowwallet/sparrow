package com.sparrowwallet.sparrow.event;

public class TimedWorkerEvent {
    private final String status;
    private final int timeMills;

    public TimedWorkerEvent(String status) {
        this.status = status;
        this.timeMills = 0;
    }

    public TimedWorkerEvent(String status, int timeMills) {
        this.status = status;
        this.timeMills = timeMills;
    }

    public String getStatus() {
        return status;
    }

    public int getTimeMills() {
        return timeMills;
    }
}
