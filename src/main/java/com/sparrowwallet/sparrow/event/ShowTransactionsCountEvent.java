package com.sparrowwallet.sparrow.event;

public class ShowTransactionsCountEvent {
    private final boolean showCount;

    public ShowTransactionsCountEvent(boolean showCount) {
        this.showCount = showCount;
    }

    public boolean isShowCount() {
        return showCount;
    }
}
