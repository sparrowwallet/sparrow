package com.sparrowwallet.sparrow.net;

public class ElectrumServerRpcException extends RuntimeException {
    public ElectrumServerRpcException() {
        super();
    }

    public ElectrumServerRpcException(String message) {
        super(message);
    }

    public ElectrumServerRpcException(Throwable cause) {
        super(cause);
    }

    public ElectrumServerRpcException(String message, Throwable cause) {
        super(message, cause);
    }
}
