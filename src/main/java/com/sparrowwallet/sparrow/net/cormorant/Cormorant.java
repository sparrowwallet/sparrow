package com.sparrowwallet.sparrow.net.cormorant;

import com.google.common.eventbus.EventBus;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.io.Server;
import com.sparrowwallet.sparrow.net.Protocol;
import com.sparrowwallet.sparrow.net.cormorant.bitcoind.BitcoindClient;
import com.sparrowwallet.sparrow.net.cormorant.bitcoind.CormorantBitcoindException;
import com.sparrowwallet.sparrow.net.cormorant.bitcoind.ImportFailedException;
import com.sparrowwallet.sparrow.net.cormorant.electrum.ElectrumServerRunnable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Cormorant {
    private static final Logger log = LoggerFactory.getLogger(Cormorant.class);

    private static final EventBus EVENT_BUS = new EventBus();

    public static final String SERVER_NAME = "Cormorant";

    private BitcoindClient bitcoindClient;
    private ElectrumServerRunnable electrumServer;

    private boolean running;

    public Server start() throws CormorantBitcoindException {
        bitcoindClient = new BitcoindClient();
        bitcoindClient.initialize();

        Thread importThread = new Thread(() -> {
            try {
                bitcoindClient.importWallets(AppServices.get().getOpenWallets().keySet());
            } catch(ImportFailedException e) {
                log.debug("Failed to import wallets", e);
            } finally {
                bitcoindClient.signalInitialImportStarted();
            }
        }, "Cormorant Initial Wallet Importer");
        importThread.setDaemon(true);
        importThread.start();

        electrumServer = new ElectrumServerRunnable(bitcoindClient);
        Thread electrumServerThread = new Thread(electrumServer, "Cormorant Electrum Server");
        electrumServerThread.setDaemon(true);
        electrumServerThread.start();

        bitcoindClient.waitUntilInitialImportStarted();

        running = true;
        return new Server(Protocol.TCP.toUrlString(com.sparrowwallet.sparrow.net.ElectrumServer.CORE_ELECTRUM_HOST, electrumServer.getPort()));
    }

    public boolean checkWalletImport(Wallet wallet) {
        //Will block until all wallet descriptors have been added
        try {
            bitcoindClient.importWallet(wallet);
            return true;
        } catch(ImportFailedException e) {
            log.debug("Failed to import wallets", e);
            return false;
        }
    }

    public boolean isRunning() {
        return running;
    }

    public void stop() {
        bitcoindClient.stop();
        if(electrumServer != null) {
            electrumServer.stop();
        }

        running = false;
    }

    public static EventBus getEventBus() {
        return EVENT_BUS;
    }
}
