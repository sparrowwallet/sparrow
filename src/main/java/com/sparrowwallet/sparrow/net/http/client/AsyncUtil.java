package com.sparrowwallet.sparrow.net.http.client;

import io.reactivex.Completable;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.functions.Action;
import io.reactivex.schedulers.Schedulers;
import org.slf4j.MDC;

import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public class AsyncUtil {
    private static final ThreadUtil threadUtil = ThreadUtil.getInstance();
    private static AsyncUtil instance;

    public static AsyncUtil getInstance() {
        if(instance == null) {
            instance = new AsyncUtil();
        }
        return instance;
    }

    public <T> T unwrapException(Callable<T> c) throws Exception {
        try {
            return c.call();
        } catch(RuntimeException e) {
            // blockingXXX wraps errors with RuntimeException, unwrap it
            throw unwrapException(e);
        }
    }

    public Exception unwrapException(Exception e) throws Exception {
        if(e.getCause() != null && e.getCause() instanceof Exception) {
            throw (Exception) e.getCause();
        }
        throw e;
    }

    public <T> T blockingGet(Single<T> o) throws Exception {
        try {
            return unwrapException(o::blockingGet);
        } catch(ExecutionException e) {
            // blockingGet(threadUtil.runWithTimeoutAndRetry()) wraps InterruptedException("exit (done)")
            // with ExecutionException, unwrap it
            throw unwrapException(e);
        }
    }

    public <T> T blockingGet(Single<T> o, long timeoutMs) throws Exception {
        Callable<T> callable = () -> blockingGet(o);
        return blockingGet(runAsync(callable, timeoutMs));
    }

    public <T> T blockingGet(Future<T> o, long timeoutMs) throws Exception {
        return o.get(timeoutMs, TimeUnit.MILLISECONDS);
    }

    public <T> T blockingLast(Observable<T> o) throws Exception {
        return unwrapException(o::blockingLast);
    }

    public void blockingAwait(Completable o) throws Exception {
        Callable<Optional> callable = () -> {
            o.blockingAwait();
            return Optional.empty();
        };
        unwrapException(callable);
    }

    public void blockingAwait(Completable o, long timeoutMs) throws Exception {
        Callable<Optional> callable = () -> {
            o.blockingAwait();
            return Optional.empty();
        };
        blockingGet(runAsync(callable, timeoutMs));
    }

    public <T> Single<T> timeout(Single<T> o, long timeoutMs) {
        try {
            return Single.just(blockingGet(o, timeoutMs));
        } catch(Exception e) {
            return Single.error(e);
        }
    }/*

    public Completable timeout(Completable o, long timeoutMs) {
        try {
            return Completable.fromCallable(() -> {
                blockingAwait(o, timeoutMs);
                return Optional.empty();
            });
        } catch (Exception e) {
            return Completable.error(e);
        }
    }*/

    public <T> Single<T> runIOAsync(final Callable<T> callable) {
        return Single.fromCallable(callable).subscribeOn(Schedulers.io());
    }

    public Completable runIOAsyncCompletable(final Action action) {
        return Completable.fromAction(action).subscribeOn(Schedulers.io());
    }

    public <T> T runIO(final Callable<T> callable) throws Exception {
        return blockingGet(runIOAsync(callable));
    }

    public void runIO(final Action action) throws Exception {
        blockingAwait(runIOAsyncCompletable(action));
    }

    public Completable runAsync(Runnable runnable, long timeoutMs) {
        Future<?> future = runAsync(() -> {
            runnable.run();
            return Optional.empty(); // must return an object for using Completable.fromSingle()
        });
        return Completable.fromSingle(Single.fromFuture(future, timeoutMs, TimeUnit.MILLISECONDS));
    }

    public <T> Future<T> runAsync(Callable<T> callable) {
        // preserve logging context
        String mdc = mdcAppend("runAsync=" + System.currentTimeMillis());
        return threadUtil.runAsync(() -> {
            MDC.put("mdc", mdc);
            return callable.call();
        });
    }

    public <T> Single<T> runAsync(Callable<T> callable, long timeoutMs) {
        Future<T> future = runAsync(callable);
        return Single.fromFuture(future, timeoutMs, TimeUnit.MILLISECONDS);
    }

    private static String mdcAppend(String info) {
        String mdc = MDC.get("mdc");
        if(mdc == null) {
            mdc = "";
        } else {
            mdc += ",";
        }
        mdc += info;
        return mdc;
    }
}
