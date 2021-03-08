package com.sparrowwallet.sparrow;

import com.sparrowwallet.drongo.LogHandler;
import com.sparrowwallet.sparrow.event.TorStatusEvent;
import org.slf4j.event.Level;

public class TorLogHandler implements LogHandler {
    @Override
    public void handleLog(String threadName, Level level, String message, String loggerName, long timestamp, StackTraceElement[] callerData) {
        EventManager.get().post(new TorStatusEvent(message));
    }
}
