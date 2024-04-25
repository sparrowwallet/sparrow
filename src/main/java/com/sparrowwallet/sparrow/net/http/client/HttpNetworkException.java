package com.sparrowwallet.sparrow.net.http.client;

public class HttpNetworkException extends HttpException {
    public HttpNetworkException(String message) {
        super(message);
    }

    public HttpNetworkException(Exception cause) {
        super(cause);
    }
}
