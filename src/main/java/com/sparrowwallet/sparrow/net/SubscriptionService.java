package com.sparrowwallet.sparrow.net;

import com.github.arteam.simplejsonrpc.core.annotation.JsonRpcMethod;
import com.github.arteam.simplejsonrpc.core.annotation.JsonRpcOptional;
import com.github.arteam.simplejsonrpc.core.annotation.JsonRpcParam;
import com.github.arteam.simplejsonrpc.core.annotation.JsonRpcService;
import com.google.common.collect.Iterables;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.event.NewBlockEvent;
import com.sparrowwallet.sparrow.event.WalletNodeHistoryChangedEvent;
import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

@JsonRpcService
public class SubscriptionService {
    private static final Logger log = LoggerFactory.getLogger(SubscriptionService.class);

    @JsonRpcMethod("blockchain.headers.subscribe")
    public void newBlockHeaderTip(@JsonRpcParam("header") final BlockHeaderTip header) {
        Platform.runLater(() -> EventManager.get().post(new NewBlockEvent(header.height, header.getBlockHeader())));
    }

    @JsonRpcMethod("blockchain.scripthash.subscribe")
    public void scriptHashStatusUpdated(@JsonRpcParam("scripthash") final String scriptHash, @JsonRpcOptional @JsonRpcParam("status") final String status) {
        List<String> existingStatuses = ElectrumServer.getSubscribedScriptHashes().get(scriptHash);
        if(existingStatuses == null) {
            log.debug("Received script hash status update for unsubscribed script hash: " + scriptHash);
            ElectrumServer.updateSubscribedScriptHashStatus(scriptHash, status);
        } else if(status != null && existingStatuses.contains(status)) {
            log.debug("Received script hash status update, but status has not changed");
            return;
        } else {
            String oldStatus = Iterables.getLast(existingStatuses);
            log.debug("Status updated for script hash " + scriptHash + ", was " + oldStatus + " now " + status);
            existingStatuses.add(status);
        }

        Platform.runLater(() -> EventManager.get().post(new WalletNodeHistoryChangedEvent(scriptHash)));
    }
}
