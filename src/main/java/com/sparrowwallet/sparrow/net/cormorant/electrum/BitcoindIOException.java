package com.sparrowwallet.sparrow.net.cormorant.electrum;

import com.github.arteam.simplejsonrpc.core.annotation.JsonRpcError;
import com.github.arteam.simplejsonrpc.core.annotation.JsonRpcErrorData;
import com.google.common.base.Throwables;

@JsonRpcError(code=-32000, message="Could not connect to Bitcoin Core RPC")
public class BitcoindIOException extends Exception {
    @JsonRpcErrorData
    private final String rootCause;

    public BitcoindIOException(Throwable rootCause) {
        super("Could not connect to Bitcoin Core RPC");
        this.rootCause = Throwables.getRootCause(rootCause).getMessage();
    }

    public String getRootCause() {
        return rootCause;
    }
}
