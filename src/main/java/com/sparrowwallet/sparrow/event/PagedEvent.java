package com.sparrowwallet.sparrow.event;

public class PagedEvent {
    private final int pageStart;
    private final int pageEnd;

    public PagedEvent(int pageStart, int pageEnd) {
        this.pageStart = pageStart;
        this.pageEnd = pageEnd;
    }

    public int getPageStart() {
        return pageStart;
    }

    public int getPageEnd() {
        return pageEnd;
    }
}
