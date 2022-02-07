package com.sparrowwallet.sparrow.event;

import javafx.scene.Node;

public class StatusEvent {
    public static final int DEFAULT_SHOW_DURATION_SECS = 20;

    private final String status;
    private final Node graphic;
    private final int showDuration;

    public StatusEvent(String status) {
        this(status, null, DEFAULT_SHOW_DURATION_SECS);
    }

    public StatusEvent(String status, Node graphic) {
        this(status, graphic, DEFAULT_SHOW_DURATION_SECS);
    }

    public StatusEvent(String status, int showDuration) {
        this(status, null, showDuration);
    }

    public StatusEvent(String status, Node graphic, int showDuration) {
        this.status = status;
        this.graphic = graphic;
        this.showDuration = showDuration;
    }

    public String getStatus() {
        return status;
    }

    public Node getGraphic() {
        return graphic;
    }

    public int getShowDuration() {
        return showDuration;
    }
}
