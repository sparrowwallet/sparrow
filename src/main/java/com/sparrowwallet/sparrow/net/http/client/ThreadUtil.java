package com.sparrowwallet.sparrow.net.http.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;

public class ThreadUtil {
    private static final Logger log = LoggerFactory.getLogger(ThreadUtil.class);

    private static ThreadUtil instance;

    private ExecutorService executorService;

    protected ThreadUtil() {
        this.executorService = computeExecutorService();
    }

    public static ThreadUtil getInstance() {
        if(instance == null) {
            instance = new ThreadUtil();
        }
        return instance;
    }

    protected ExecutorService computeExecutorService() {
        return Executors.newFixedThreadPool(5,
                r -> {
                    Thread t = Executors.defaultThreadFactory().newThread(r);
                    t.setDaemon(true);
                    return t;
                });
    }

    public void setExecutorService(ScheduledExecutorService executorService) {
        this.executorService = executorService;
    }

    public <T> Future<T> runAsync(Callable<T> callable) {
        return executorService.submit(callable);
    }
}
