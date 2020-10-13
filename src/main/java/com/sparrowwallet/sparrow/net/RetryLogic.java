package com.sparrowwallet.sparrow.net;

import java.util.List;

/**
 * Generic retry logic. Delegate must throw the specified exception type to trigger the retry logic.
 */
public class RetryLogic<T> {
    public static interface Delegate<T> {
        T call() throws Exception;
    }

    private final int maxAttempts;
    private final int retryWaitSeconds;
    @SuppressWarnings("rawtypes")
    private final List<Class> retryExceptionTypes;

    public RetryLogic(int maxAttempts, int retryWaitSeconds, @SuppressWarnings("rawtypes") Class retryExceptionType) {
        this(maxAttempts, retryWaitSeconds, List.of(retryExceptionType));
    }

    public RetryLogic(int maxAttempts, int retryWaitSeconds, @SuppressWarnings("rawtypes") List<Class> retryExceptionTypes) {
        this.maxAttempts = maxAttempts;
        this.retryWaitSeconds = retryWaitSeconds;
        this.retryExceptionTypes = retryExceptionTypes;
    }

    public T getResult(Delegate<T> caller) throws Exception {
        T result = null;
        int remainingAttempts = maxAttempts;
        do {
            try {
                return caller.call();
            } catch(Exception e) {
                if(retryExceptionTypes.contains(e.getClass())) {
                    if(--remainingAttempts == 0) {
                        throw new ServerException("Retries exhausted", e);
                    } else {
                        try {
                            Thread.sleep((1000 * retryWaitSeconds));
                        } catch(InterruptedException ie) {
                            //ignore
                        }
                    }
                } else {
                    throw e;
                }
            }

        } while(remainingAttempts > 0);

        throw new IllegalStateException("Should be impossible");
    }
}