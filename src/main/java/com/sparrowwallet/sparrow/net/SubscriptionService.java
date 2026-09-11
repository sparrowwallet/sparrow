package com.sparrowwallet.sparrow.net;

import com.github.arteam.simplejsonrpc.core.annotation.JsonRpcMethod;
import com.github.arteam.simplejsonrpc.core.annotation.JsonRpcOptional;
import com.github.arteam.simplejsonrpc.core.annotation.JsonRpcParam;
import com.github.arteam.simplejsonrpc.core.annotation.JsonRpcService;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.event.NewBlockEvent;
import com.sparrowwallet.sparrow.event.WalletNodeHistoryChangedEvent;
import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@JsonRpcService
public class SubscriptionService {
    private static final Logger log = LoggerFactory.getLogger(SubscriptionService.class);

    @JsonRpcMethod("blockchain.headers.subscribe")
    public void newBlockHeaderTip(@JsonRpcParam("header") final BlockHeaderTip header) {
        String tipError = ElectrumServer.getAnnouncedTipValidationError(header);
        if(tipError != null) {
            ElectrumServer.warnInvalidTip(tipError);
            return;
        }

        ElectrumServer.updateTipReceived(header.height);
        ElectrumServer.updateRetrievedBlockHeaders(header.height, header.getBlockHeader());
        Platform.runLater(() -> EventManager.get().post(new NewBlockEvent(header.height, header.getBlockHeader())));
    }

    @JsonRpcMethod("blockchain.scripthash.subscribe")
    public void scriptHashStatusUpdated(@JsonRpcParam("scripthash") final String scriptHash, @JsonRpcOptional @JsonRpcParam("status") final String status) {
        Map<String, String> subscribedScriptHashes = ElectrumServer.getSubscribedScriptHashes();
        if(!subscribedScriptHashes.containsKey(scriptHash)) {
            log.trace("Received script hash status update for non-wallet script hash: " + scriptHash);
        } else if(Objects.equals(status, subscribedScriptHashes.get(scriptHash))) {
            log.debug("Received script hash status update, but status has not changed");
            return;
        } else {
            log.debug("Status updated for script hash " + scriptHash + ", was " + subscribedScriptHashes.get(scriptHash) + " now " + status);
            subscribedScriptHashes.put(scriptHash, status);
        }

        Platform.runLater(() -> EventManager.get().post(new WalletNodeHistoryChangedEvent(scriptHash, status)));
    }

    @JsonRpcMethod("blockchain.silentpayments.subscribe")
    public void silentPaymentsUpdate(@JsonRpcParam("subscription") final SilentPaymentsSubscription subscription, @JsonRpcParam("progress") final double progress, @JsonRpcParam("history") final List<SilentPaymentsTx> history) {
        String silentPaymentAddress = subscription.address;
        SilentPaymentsScanCache cache = ElectrumServer.getScanCache(silentPaymentAddress);
        if(cache == null) {
            log.trace("Received silent payments notification for unknown subscription: " + silentPaymentAddress);
            return;
        }

        //A notification can reach the read thread before the subscribe response has been recorded, so the cache decides
        //whether to apply it now, hold it until the canonical start height is known, or drop it as being from a prior subscribe
        cache.lock();
        try {
            ElectrumServer.postSilentPaymentsNotified(silentPaymentAddress,
                    cache.applyOrHold(subscription.start_height, TcpTransport.getDeliveredResponses(), progress, history));
        } finally {
            cache.unlock();
        }
    }
}
