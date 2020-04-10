package com.sparrowwallet.sparrow;

import com.google.common.eventbus.EventBus;

public class EventManager {
    private static EventBus SINGLETON = new EventBus();

    private EventManager() {}

    public static EventBus get() {
        return SINGLETON;
    }
}
