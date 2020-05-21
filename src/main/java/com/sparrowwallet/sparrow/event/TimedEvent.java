package com.sparrowwallet.sparrow.event;

public class TimedEvent {
    private final Action action;
    private final String status;
    protected int timeMills;

    public TimedEvent(Action action, String status) {
        this.action = action;
        this.status = status;
        this.timeMills = 0;
    }

    public TimedEvent(Action action, String status, int timeMills) {
        this.action = action;
        this.status = status;
        this.timeMills = timeMills;
    }

    public Action getAction() {
        return action;
    }

    public String getStatus() {
        return status;
    }

    public int getTimeMills() {
        return timeMills;
    }

    public enum Action {
        START, END;
    }
}
