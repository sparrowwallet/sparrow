package com.sparrowwallet.sparrow.net;

import com.github.arteam.simplejsonrpc.core.annotation.JsonRpcMethod;
import com.github.arteam.simplejsonrpc.core.annotation.JsonRpcParam;
import com.github.arteam.simplejsonrpc.core.annotation.JsonRpcService;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.event.NewBlockEvent;
import com.sparrowwallet.sparrow.event.WalletNodeHistoryChangedEvent;
import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

@JsonRpcService
public class SubscriptionService {
    private static final Logger log = LoggerFactory.getLogger(SubscriptionService.class);

    @JsonRpcMethod("blockchain.headers.subscribe")
    public void newBlockHeaderTip(@JsonRpcParam("header") final BlockHeaderTip header) {
        Platform.runLater(() -> EventManager.get().post(new NewBlockEvent(header.height, header.getBlockHeader())));
    }

    @JsonRpcMethod("blockchain.scripthash.subscribe")
    public void scriptHashStatusUpdated(@JsonRpcParam("scripthash") final String scriptHash, @JsonRpcParam("status") final String status) {
        String oldStatus = ElectrumServer.getSubscribedScriptHashes().put(scriptHash, status);
        if(Objects.equals(oldStatus, status)) {
            log.warn("Received script hash status update, but status has not changed");
        }

        Platform.runLater(() -> EventManager.get().post(new WalletNodeHistoryChangedEvent(scriptHash)));
    }
}
