package com.sparrowwallet.sparrow;

import com.sparrowwallet.drongo.LogHandler;
import com.sparrowwallet.sparrow.event.TorStatusEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

public class TorLogHandler implements LogHandler {
    private static final Logger log = LoggerFactory.getLogger(TorLogHandler.class);

    @Override
    public void handleLog(String threadName, Level level, String message, String loggerName, long timestamp, StackTraceElement[] callerData) {
        log.debug(message);
        EventManager.get().post(new TorStatusEvent(message));
    }
}
