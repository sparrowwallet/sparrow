package com.sparrowwallet.sparrow.net.http.client;

import java.util.Map;

public interface IBackendClient {
    <T> T getJson(String url, Class<T> responseType, Map<String, String> headers) throws HttpException;

    <T> T getJson(String url, Class<T> responseType, Map<String, String> headers, boolean async) throws HttpException;

    <T> T postUrlEncoded(String url, Class<T> responseType, Map<String, String> headers, Map<String, String> body) throws Exception;
}
