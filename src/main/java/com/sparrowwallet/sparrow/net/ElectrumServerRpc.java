package com.sparrowwallet.sparrow.net;

import com.github.arteam.simplejsonrpc.client.Transport;
import com.sparrowwallet.drongo.wallet.Wallet;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface ElectrumServerRpc {
    void ping(Transport transport);

    List<String> getServerVersion(Transport transport, String clientName, String[] supportedVersions);

    String getServerBanner(Transport transport);

    BlockHeaderTip subscribeBlockHeaders(Transport transport);

    Map<String, ScriptHashTx[]> getScriptHashHistory(Transport transport, Wallet wallet, Map<String, String> pathScriptHashes, boolean failOnError);

    Map<String, ScriptHashTx[]> getScriptHashMempool(Transport transport, Wallet wallet, Map<String, String> pathScriptHashes, boolean failOnError);

    Map<String, String> subscribeScriptHashes(Transport transport, Wallet wallet, Map<String, String> pathScriptHashes);

    Map<Integer, String> getBlockHeaders(Transport transport, Wallet wallet, Set<Integer> blockHeights);

    Map<String, String> getTransactions(Transport transport, Wallet wallet, Set<String> txids);

    Map<String, VerboseTransaction> getVerboseTransactions(Transport transport, Set<String> txids, String scriptHash);

    Map<Integer, Double> getFeeEstimates(Transport transport, List<Integer> targetBlocks);

    Map<Long, Long> getFeeRateHistogram(Transport transport);

    Double getMinimumRelayFee(Transport transport);

    String broadcastTransaction(Transport transport, String txHex);

    long getIdCounterValue();
}
