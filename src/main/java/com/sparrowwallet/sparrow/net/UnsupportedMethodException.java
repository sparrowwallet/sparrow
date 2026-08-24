package com.sparrowwallet.sparrow.net;

/**
 * Thrown when the connected server does not implement a method, as reported by the JSON-RPC method not found error code.
 * Unlike other errors this is a property of the server rather than of the request, so callers disable the feature for the session
 * rather than treating the response as a refusal.
 */
public class UnsupportedMethodException extends ElectrumServerRpcException {
    private final String method;

    public UnsupportedMethodException(String method, Throwable cause) {
        super("Server does not support " + method, cause);
        this.method = method;
    }

    public String getMethod() {
        return method;
    }
}
