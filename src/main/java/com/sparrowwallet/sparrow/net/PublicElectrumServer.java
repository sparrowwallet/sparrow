package com.sparrowwallet.sparrow.net;

public enum PublicElectrumServer {
    BLOCKSTREAM_INFO("blockstream.info", "ssl://blockstream.info:700"),
    ELECTRUM_BLOCKSTREAM_INFO("electrum.blockstream.info", "ssl://electrum.blockstream.info:50002"),
    LUKECHILDS_CO("bitcoin.lukechilds.co", "ssl://bitcoin.lukechilds.co:50002"),
    EMZY_DE("electrum.emzy.de", "ssl://electrum.emzy.de:50002"),
    BITAROO_NET("electrum.bitaroo.net", "ssl://electrum.bitaroo.net:50002");

    PublicElectrumServer(String name, String url) {
        this.name = name;
        this.url = url;
    }

    private final String name;
    private final String url;

    public String getName() {
        return name;
    }

    public String getUrl() {
        return url;
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
