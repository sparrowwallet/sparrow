package com.sparrowwallet.sparrow.net;

import com.google.common.net.HostAndPort;
import com.sparrowwallet.drongo.Network;
import com.sparrowwallet.sparrow.io.Server;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public enum PublicElectrumServer {
    BLOCKSTREAM_INFO("blockstream.info", "ssl://blockstream.info:700", Network.MAINNET),
    ELECTRUM_BLOCKSTREAM_INFO("electrum.blockstream.info", "ssl://electrum.blockstream.info:50002", Network.MAINNET),
    LUKECHILDS_CO("bitcoin.lu.ke", "ssl://bitcoin.lu.ke:50002", Network.MAINNET),
    EMZY_DE("electrum.emzy.de", "ssl://electrum.emzy.de:50002", Network.MAINNET),
    BITAROO_NET("electrum.bitaroo.net", "ssl://electrum.bitaroo.net:50002", Network.MAINNET),
    DIYNODES_COM("electrum.diynodes.com", "ssl://electrum.diynodes.com:50022", Network.MAINNET),
    SETHFORPRIVACY_COM("fulcrum.sethforprivacy.com", "ssl://fulcrum.sethforprivacy.com:50002", Network.MAINNET),
    TESTNET_ARANGUREN_ORG("testnet.aranguren.org", "ssl://testnet.aranguren.org:51002", Network.TESTNET),
    TESTNET_QTORNADO_COM("testnet.qtornado.com", "ssl://testnet.qtornado.com:51002", Network.TESTNET),
    SIGNET_MEMPOOL_SPACE("mempool.space", "ssl://mempool.space:60602", Network.SIGNET),
    TESTNET4_MEMPOOL_SPACE("mempool.space", "ssl://mempool.space:40002", Network.TESTNET4),
    TESTNET4_C3_SOFT("blackie.c3-soft.com", "ssl://blackie.c3-soft.com:57010", Network.TESTNET4);

    PublicElectrumServer(String name, String url, Network network) {
        this.server = new Server(url, name);
        this.network = network;
    }

    public static final List<Network> SUPPORTED_NETWORKS = List.of(Network.MAINNET, Network.TESTNET, Network.SIGNET, Network.TESTNET4);

    private final Server server;
    private final Network network;

    public Server getServer() {
        return server;
    }

    public String getUrl() {
        return server.getUrl();
    }

    public Network getNetwork() {
        return network;
    }

    public static List<PublicElectrumServer> getServers() {
        return Arrays.stream(values()).filter(server -> server.network == Network.get()).collect(Collectors.toList());
    }

    public static boolean supportedNetwork() {
        return SUPPORTED_NETWORKS.contains(Network.get());
    }

    public static PublicElectrumServer fromServer(Server server) {
        for(PublicElectrumServer publicServer : values()) {
            if(publicServer.getServer().equals(server)) {
                return publicServer;
            }
        }

        return null;
    }

    public static boolean isPublicServer(HostAndPort hostAndPort) {
        for(PublicElectrumServer publicServer : values()) {
            if(publicServer.getServer().getHostAndPort().equals(hostAndPort)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public String toString() {
        return server.getAlias();
    }
}
