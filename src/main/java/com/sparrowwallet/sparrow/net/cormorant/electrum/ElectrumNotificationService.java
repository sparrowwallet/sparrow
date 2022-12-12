package com.sparrowwallet.sparrow.net.cormorant.electrum;

import com.github.arteam.simplejsonrpc.core.annotation.JsonRpcMethod;
import com.github.arteam.simplejsonrpc.core.annotation.JsonRpcOptional;
import com.github.arteam.simplejsonrpc.core.annotation.JsonRpcParam;
import com.github.arteam.simplejsonrpc.core.annotation.JsonRpcService;

@JsonRpcService
public interface ElectrumNotificationService {
    @JsonRpcMethod("blockchain.headers.subscribe")
    void notifyHeaders(@JsonRpcParam("header") ElectrumBlockHeader electrumBlockHeader);

    @JsonRpcMethod("blockchain.scripthash.subscribe")
    void notifyScriptHash(@JsonRpcParam("scripthash") String scriptHash, @JsonRpcOptional @JsonRpcParam("status") String status);
}
