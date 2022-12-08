package com.sparrowwallet.sparrow.net.cormorant.electrum;

import com.github.arteam.simplejsonrpc.core.annotation.JsonRpcError;
import com.github.arteam.simplejsonrpc.core.domain.ErrorMessage;

@JsonRpcError(code=-32003)
public class BroadcastFailedException extends Exception {
    private final String message;

    public BroadcastFailedException(ErrorMessage errorMessage) {
        this.message = errorMessage == null ? "" : errorMessage.getMessage() + (errorMessage.getData() == null ? "" : " (" + errorMessage.getData() + ")");
    }

    public String getMessage() {
        return message;
    }
}
