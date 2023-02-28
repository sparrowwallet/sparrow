package com.sparrowwallet.sparrow.net;

import com.sparrowwallet.sparrow.io.Server;

public enum BlockExplorer {
    MEMPOOL_SPACE("https://mempool.space"),
    BLOCKSTREAM_INFO("https://blockstream.info"),
    NONE("http://none");

    private final Server server;

    BlockExplorer(String url) {
        this.server = new Server(url);
    }

    public Server getServer() {
        return server;
    }
}
