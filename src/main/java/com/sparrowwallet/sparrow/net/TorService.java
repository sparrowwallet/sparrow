package com.sparrowwallet.sparrow.net;

import io.matthewnelson.kmp.tor.runtime.Action;
import io.matthewnelson.kmp.tor.runtime.TorListeners;
import io.matthewnelson.kmp.tor.runtime.TorRuntime;
import io.matthewnelson.kmp.tor.runtime.core.OnEvent;
import io.matthewnelson.kmp.tor.runtime.core.ctrl.Reply;
import javafx.concurrent.ScheduledService;
import javafx.concurrent.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Service to start internal Tor (including a Tor proxy running on localhost:9050, or another port if unavailable)
 *
 * This is a ScheduledService to take advantage of the retry on failure behaviour
 */
public class TorService extends ScheduledService<Tor> {
    private static final Logger log = LoggerFactory.getLogger(TorService.class);

    private final ReentrantLock startupLock = new ReentrantLock();
    private final Condition startupCondition = startupLock.newCondition();

    @Override
    protected Task<Tor> createTask() {
        return new Task<>() {
            private Exception startupException;

            @Override
            protected Tor call() throws Exception {
                Tor tor = Tor.getDefault();
                if(tor == null) {
                    tor = new Tor(new StartupListener());
                    TorRuntime torRuntime = tor.getTorRuntime();

                    torRuntime.enqueue(Action.StartDaemon, throwable -> {
                        if(throwable instanceof Reply.Error replyError && !replyError.replies.isEmpty()) {
                            startupException = new TorStartupException(replyError.replies.getFirst().message);
                        } else if(throwable instanceof Exception exception) {
                            startupException = exception;
                        } else {
                            startupException = new Exception(throwable);
                        }
                        log.error("Error starting Tor daemon", throwable);
                        try {
                            startupLock.lock();
                            startupCondition.signalAll();
                        } finally {
                            startupLock.unlock();
                        }
                    }, _ -> log.info("Tor daemon started successfully"));

                    try {
                        startupLock.lock();
                        if(!startupCondition.await(5, TimeUnit.MINUTES)) {
                            throw new TorStartupException("Tor failed to start after 5 minutes, giving up");
                        }

                        if(startupException != null) {
                            throw startupException;
                        }
                    } finally {
                        startupLock.unlock();
                    }
                }

                return tor;
            }
        };
    }

    private class StartupListener implements OnEvent<TorListeners> {
        @Override
        public void invoke(TorListeners torListeners) {
            if(!torListeners.socks.isEmpty()) {
                try {
                    startupLock.lock();
                    startupCondition.signalAll();
                } finally {
                    startupLock.unlock();
                }
            }
        }
    }
}
