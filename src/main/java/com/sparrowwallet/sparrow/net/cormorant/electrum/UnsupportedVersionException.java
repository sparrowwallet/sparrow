package com.sparrowwallet.sparrow.net.cormorant.electrum;

import com.github.arteam.simplejsonrpc.core.annotation.JsonRpcError;
import com.github.arteam.simplejsonrpc.core.annotation.JsonRpcErrorData;

@JsonRpcError(code=-32003, message="Unsupported version")
public class UnsupportedVersionException extends Exception {
    @JsonRpcErrorData
    private final String version;

    public UnsupportedVersionException(String version) {
        this.version = version;
    }

    public String getVersion() {
        return version;
    }
}
