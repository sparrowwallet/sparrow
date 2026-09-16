package com.sparrowwallet.sparrow;

import javafx.application.Platform;
import org.junit.jupiter.api.Assertions;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

public final class JavaFxTestSupport {
    private static boolean started;

    private JavaFxTestSupport() {
    }

    public static synchronized void startJavaFx() throws InterruptedException {
        if(started) {
            return;
        }

        System.setProperty("glass.platform", "Headless");
        CountDownLatch startupLatch = new CountDownLatch(1);
        Platform.startup(startupLatch::countDown);
        Assertions.assertTrue(startupLatch.await(10, TimeUnit.SECONDS));
        started = true;
    }

    public static void runOnFxThread(Runnable runnable) throws Exception {
        FutureTask<Void> task = new FutureTask<>(runnable, null);
        Platform.runLater(task);
        task.get(10, TimeUnit.SECONDS);
    }
}
