package com.sparrowwallet.sparrow.net.http.client;

import io.reactivex.Single;

import java.util.Map;
import java.util.Optional;

public interface IHttpClient extends IBackendClient {
    void connect() throws Exception;

    <T> Single<Optional<T>> postJson(String url, Class<T> responseType, Map<String, String> headers, Object body);

    Single<Optional<String>> postString(String urlStr, Map<String, String> headers, String contentType, String content);
}
