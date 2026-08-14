package com.sparrowwallet.sparrow.net;

import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.io.Server;
import org.girod.javafx.svgimage.SVGImage;
import org.girod.javafx.svgimage.SVGLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.util.Locale;

public enum BlockExplorer {
    MEMPOOL_SPACE("https://mempool.space"),
    BLOCKSTREAM_INFO("https://blockstream.info"),
    NONE("http://none");

    private static final Logger log = LoggerFactory.getLogger(BlockExplorer.class);

    private final Server server;

    BlockExplorer(String url) {
        this.server = new Server(url);
    }

    public Server getServer() {
        return server;
    }

    public static SVGImage getSVGImage(Server server) {
        try {
            URL url = AppServices.class.getResource("/image/blockexplorer/" + server.getHost().toLowerCase(Locale.ROOT) + "-icon.svg");
            if(url != null) {
                return SVGLoader.load(url);
            }
        } catch(Exception e) {
            log.error("Could not load block explorer image for " + server.getHost());
        }

        return null;
    }
}
