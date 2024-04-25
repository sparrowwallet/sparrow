package com.sparrowwallet.sparrow.net.http.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.reactivex.Single;
import io.reactivex.schedulers.Schedulers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

@SuppressWarnings("unchecked")
public abstract class JacksonHttpClient implements IHttpClient {
    private static final Logger log = LoggerFactory.getLogger(JacksonHttpClient.class);

    private final Consumer<Exception> onNetworkError;

    public JacksonHttpClient(Consumer<Exception> onNetworkError) {
        this.onNetworkError = onNetworkError;
    }

    protected abstract String requestJsonGet(String urlStr, Map<String, String> headers, boolean async) throws HttpException;

    protected abstract String requestJsonPost(String urlStr, Map<String, String> headers, String jsonBody) throws HttpException;

    protected abstract String requestStringPost(String urlStr, Map<String, String> headers, String contentType, String content) throws HttpException;

    protected abstract String requestJsonPostUrlEncoded(String urlStr, Map<String, String> headers, Map<String, String> body) throws HttpException;

    @Override
    public <T> T getJson(String urlStr, Class<T> responseType, Map<String, String> headers) throws HttpException {
        return getJson(urlStr, responseType, headers, false);
    }

    @Override
    public <T> T getJson(String urlStr, Class<T> responseType, Map<String, String> headers, boolean async) throws HttpException {
        return httpObservableBlockingSingle(() -> { // run on ioThread
            try {
                String responseContent = handleNetworkError("getJson " + urlStr, () -> requestJsonGet(urlStr, headers, async));
                return parseJson(responseContent, responseType, 200);
            } catch(Exception e) {
                if(log.isDebugEnabled()) {
                    log.error("getJson failed: " + urlStr + ": " + e.toString());
                }
                throw e;
            }
        });
    }

    @Override
    public <T> Single<Optional<T>> postJson(final String urlStr, final Class<T> responseType, final Map<String, String> headers, final Object bodyObj) {
        return httpObservable(
                () -> {
                    try {
                        String jsonBody = getObjectMapper().writeValueAsString(bodyObj);
                        String responseContent = handleNetworkError("postJson " + urlStr, () -> requestJsonPost(urlStr, headers, jsonBody));
                        return parseJson(responseContent, responseType, 200);
                    } catch(HttpException e) {
                        if(log.isDebugEnabled()) {
                            log.error("postJson failed: " + urlStr + ": " + e);
                        }
                        throw e;
                    }
                });
    }

    @Override
    public Single<Optional<String>> postString(String urlStr, Map<String, String> headers, String contentType, String content) {
        return httpObservable(
                () -> {
                    try {
                        return handleNetworkError("postString " + urlStr, () -> requestStringPost(urlStr, headers, contentType, content));
                    } catch(HttpException e) {
                        if(log.isDebugEnabled()) {
                            log.error("postJson failed: " + urlStr + ": " + e.toString());
                        }
                        throw e;
                    }
                });
    }

    @Override
    public <T> T postUrlEncoded(String urlStr, Class<T> responseType, Map<String, String> headers, Map<String, String> body) throws HttpException {
        return httpObservableBlockingSingle(() -> { // run on ioThread
            try {
                String responseContent = handleNetworkError("postUrlEncoded " + urlStr, () -> requestJsonPostUrlEncoded(urlStr, headers, body));
                return parseJson(responseContent, responseType, 200);
            } catch(Exception e) {
                if(log.isDebugEnabled()) {
                    log.error("postUrlEncoded failed: " + urlStr + ": " + e);
                }
                throw e;
            }
        });
    }

    private <T> T parseJson(String responseContent, Class<T> responseType, int statusCode) throws HttpException {
        T result;
        if(log.isTraceEnabled()) {
            String responseStr = (responseContent != null ? responseContent : "null");
            if(responseStr.length() > 500) {
                responseStr = responseStr.substring(0, 500) + "...";
            }
            log.trace("response[" + (responseType != null ? responseType.getCanonicalName() : "null") + "]: " + responseStr);
        }
        if(String.class.equals(responseType)) {
            result = (T) responseContent;
        } else {
            try {
                result = getObjectMapper().readValue(responseContent, responseType);
            } catch(Exception e) {
                throw new HttpResponseException(e, responseContent, statusCode);
            }
        }
        return result;
    }

    protected String handleNetworkError(String logInfo, Callable<String> doHttpRequest) throws HttpException {
        try {
            try {
                // first attempt
                return doHttpRequest.call();
            } catch(HttpNetworkException e) {
                if(log.isDebugEnabled()) {
                    log.warn("HTTP_ERROR_NETWORK " + logInfo + ", retrying: " + e.getMessage());
                }
                // change tor proxy
                onNetworkError(e);

                // retry second attempt
                return doHttpRequest.call();
            }
        } catch(HttpException e) { // forward
            throw e;
        } catch(Exception e) { // should never happen
            throw new HttpSystemException(e);
        }
    }

    protected void onNetworkError(HttpNetworkException e) {
        if(onNetworkError != null) {
            synchronized(JacksonHttpClient.class) { // avoid overlapping Tor restarts between httpClients
                onNetworkError.accept(e);
            }
        }
    }

    protected <T> Single<Optional<T>> httpObservable(final Callable<T> supplier) {
        return Single.fromCallable(() -> Optional.ofNullable(supplier.call())).subscribeOn(Schedulers.io());
    }

    protected <T> T httpObservableBlockingSingle(final Callable<T> supplier) throws HttpException {
        try {
            Optional<T> opt = AsyncUtil.getInstance().blockingGet(httpObservable(supplier));
            return opt.orElse(null);
        } catch(HttpException e) { // forward
            throw e;
        } catch(Exception e) { // should never happen
            throw new HttpNetworkException(e);
        }
    }

    protected ObjectMapper getObjectMapper() {
        return JSONUtils.getInstance().getObjectMapper();
    }
}
