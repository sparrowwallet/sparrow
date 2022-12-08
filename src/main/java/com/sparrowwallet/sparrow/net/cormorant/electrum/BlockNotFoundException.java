package com.sparrowwallet.sparrow.net.cormorant.electrum;

import com.github.arteam.simplejsonrpc.core.annotation.JsonRpcError;
import com.github.arteam.simplejsonrpc.core.domain.ErrorMessage;

@JsonRpcError(code=-32002)
public class BlockNotFoundException extends Exception {
    private final String message;

    public BlockNotFoundException(ErrorMessage errorMessage) {
        this.message = errorMessage == null ? "" : errorMessage.getMessage() + (errorMessage.getData() == null ? "" : " (" + errorMessage.getData() + ")");
    }

    @Override
    public String getMessage() {
        return message;
    }
}
