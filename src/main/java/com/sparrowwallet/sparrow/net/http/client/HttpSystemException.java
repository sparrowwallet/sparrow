package com.sparrowwallet.sparrow.net.http.client;

public class HttpSystemException extends HttpException {
    public HttpSystemException(String message) {
        super(message);
    }

    public HttpSystemException(Exception cause) {
        super(cause);
    }
}
