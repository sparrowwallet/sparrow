package com.sparrowwallet.sparrow.net;

public class ServerConfigException extends ServerException {
    public ServerConfigException() {
    }

    public ServerConfigException(String message) {
        super(message);
    }

    public ServerConfigException(Throwable cause) {
        super(cause);
    }

    public ServerConfigException(String message, Throwable cause) {
        super(message, cause);
    }
}
