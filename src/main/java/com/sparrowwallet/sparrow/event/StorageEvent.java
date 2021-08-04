package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.sparrow.io.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class StorageEvent extends TimedEvent {
    private static final Logger log = LoggerFactory.getLogger(StorageEvent.class);

    private static boolean firstRunDone = false;
    private static final Map<String, Long> eventTime = new HashMap<>();

    public StorageEvent(String walletId, Action action, String status) {
        super(action, status);

        Integer keyDerivationPeriod = Config.get().getKeyDerivationPeriod();
        if(keyDerivationPeriod == null) {
            keyDerivationPeriod = -1;
        }

        if(action == Action.START) {
            eventTime.put(walletId, System.currentTimeMillis());
            timeMills = keyDerivationPeriod;
        } else if(action == Action.END) {
            Long start = eventTime.get(walletId);
            if(start == null) {
                log.error("Could not find start event time for wallet id " + walletId);
            } else if(firstRunDone) {
                keyDerivationPeriod = (int)(System.currentTimeMillis() - start);
                Config.get().setKeyDerivationPeriod(keyDerivationPeriod);
            }
            firstRunDone = true;
            timeMills = 0;
        }
    }
}
