package com.sparrowwallet.sparrow.net;

import com.sparrowwallet.drongo.Network;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public enum PublicElectrumServer {
    BLOCKSTREAM_INFO("blockstream.info", "ssl://blockstream.info:700", Network.MAINNET),
    ELECTRUM_BLOCKSTREAM_INFO("electrum.blockstream.info", "ssl://electrum.blockstream.info:50002", Network.MAINNET),
    LUKECHILDS_CO("bitcoin.lukechilds.co", "ssl://bitcoin.lukechilds.co:50002", Network.MAINNET),
    EMZY_DE("electrum.emzy.de", "ssl://electrum.emzy.de:50002", Network.MAINNET),
    BITAROO_NET("electrum.bitaroo.net", "ssl://electrum.bitaroo.net:50002", Network.MAINNET),
    TESTNET_ARANGUREN_ORG("testnet.aranguren.org", "ssl://testnet.aranguren.org:51002", Network.TESTNET);

    PublicElectrumServer(String name, String url, Network network) {
        this.name = name;
        this.url = url;
        this.network = network;
    }

    public static final List<Network> SUPPORTED_NETWORKS = List.of(Network.MAINNET, Network.TESTNET);

    private final String name;
    private final String url;
    private final Network network;

    public String getName() {
        return name;
    }

    public String getUrl() {
        return url;
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

    public static PublicElectrumServer fromUrl(String url) {
        for(PublicElectrumServer server : values()) {
            if(server.url.equals(url)) {
                return server;
            }
        }

        return null;
    }

    @Override
    public String toString() {
        return name;
    }
}
