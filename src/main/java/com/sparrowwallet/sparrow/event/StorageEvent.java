package com.sparrowwallet.sparrow.event;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class StorageEvent extends TimedEvent {
    private static boolean firstRunDone = false;
    private static int keyDerivationPeriod = -1;
    private static final Map<File, Long> eventTime = new HashMap<>();

    public StorageEvent(File file, Action action, String status) {
        super(action, status);

        if(action == Action.START) {
            eventTime.put(file, System.currentTimeMillis());
            timeMills = keyDerivationPeriod;
        } else if(action == Action.END) {
            long start = eventTime.get(file);
            if(firstRunDone) {
                keyDerivationPeriod = (int)(System.currentTimeMillis() - start);
            }
            firstRunDone = true;
            System.out.println(keyDerivationPeriod);
            timeMills = 0;
        }
    }
}
