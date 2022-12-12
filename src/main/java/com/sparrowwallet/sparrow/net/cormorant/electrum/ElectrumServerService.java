package com.sparrowwallet.sparrow.net.cormorant.electrum;

import com.github.arteam.simplejsonrpc.client.exception.JsonRpcException;
import com.github.arteam.simplejsonrpc.core.annotation.JsonRpcMethod;
import com.github.arteam.simplejsonrpc.core.annotation.JsonRpcOptional;
import com.github.arteam.simplejsonrpc.core.annotation.JsonRpcParam;
import com.github.arteam.simplejsonrpc.core.annotation.JsonRpcService;
import com.sparrowwallet.sparrow.SparrowWallet;
import com.sparrowwallet.sparrow.net.Version;
import com.sparrowwallet.sparrow.net.cormorant.Cormorant;
import com.sparrowwallet.sparrow.net.cormorant.bitcoind.*;
import com.sparrowwallet.sparrow.net.cormorant.index.TxEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

@JsonRpcService
public class ElectrumServerService {
    private static final Logger log = LoggerFactory.getLogger(ElectrumServerService.class);
    private static final Version VERSION = new Version("1.4");
    private static final long VSIZE_BIN_WIDTH = 50000;
    private static final double DEFAULT_FEE_RATE = 0.00001d;

    private final BitcoindClient bitcoindClient;
    private final RequestHandler requestHandler;

    public ElectrumServerService(BitcoindClient bitcoindClient, RequestHandler requestHandler) {
        this.bitcoindClient = bitcoindClient;
        this.requestHandler = requestHandler;
    }

    @JsonRpcMethod("server.version")
    public List<String> getServerVersion(@JsonRpcParam("client_name") String clientName, @JsonRpcParam("protocol_version") String protocolVersion) throws UnsupportedVersionException {
        Version clientVersion = new Version(protocolVersion);
        if(clientVersion.compareTo(VERSION) < 0) {
            throw new UnsupportedVersionException(protocolVersion);
        }

        return List.of(Cormorant.SERVER_NAME + " " + SparrowWallet.APP_VERSION, VERSION.get());
    }

    @JsonRpcMethod("server.banner")
    public String getServerBanner() {
        return SparrowWallet.APP_NAME + " " + SparrowWallet.APP_VERSION + "\n" + bitcoindClient.getNetworkInfo().subversion();
    }

    @JsonRpcMethod("blockchain.estimatefee")
    public Double estimateFee(@JsonRpcParam("number") int blocks) throws BitcoindIOException {
        try {
            FeeInfo feeInfo = bitcoindClient.getBitcoindService().estimateSmartFee(blocks);
            if(feeInfo == null || feeInfo.feerate() == null) {
                return DEFAULT_FEE_RATE;
            }

            return feeInfo.feerate();
        } catch(IllegalStateException e) {
            throw new BitcoindIOException(e);
        }
    }

    @JsonRpcMethod("mempool.get_fee_histogram")
    public List<List<Number>> getFeeHistogram() throws BitcoindIOException {
        try {
            Map<String, MempoolEntry> mempoolEntries = bitcoindClient.getBitcoindService().getRawMempool(true);

            List<VsizeFeerate> vsizeFeerates = mempoolEntries.values().stream().map(entry -> new VsizeFeerate(entry.vsize(), entry.fees().base())).sorted().toList();

            List<List<Number>> histogram = new ArrayList<>();
            long binSize = 0;
            double lastFeerate = 0.0;

            for(VsizeFeerate vsizeFeerate : vsizeFeerates) {
                if(binSize > VSIZE_BIN_WIDTH && Math.abs(lastFeerate - vsizeFeerate.feerate) > 0.0d) {
                    // vsize of transactions paying >= last_feerate
                    histogram.add(List.of(lastFeerate, binSize));
                    binSize = 0;
                }
                binSize += vsizeFeerate.vsize;
                lastFeerate = vsizeFeerate.feerate;
            }

            if(binSize > 0) {
                histogram.add(List.of(lastFeerate, binSize));
            }

            return histogram;
        } catch(IllegalStateException e) {
            throw new BitcoindIOException(e);
        }
    }

    @JsonRpcMethod("blockchain.relayfee")
    public Double getRelayFee() throws BitcoindIOException {
        try {
            MempoolInfo mempoolInfo = bitcoindClient.getBitcoindService().getMempoolInfo();
            return mempoolInfo.minrelaytxfee();
        } catch(IllegalStateException e) {
            throw new BitcoindIOException(e);
        }
    }

    @JsonRpcMethod("blockchain.headers.subscribe")
    public ElectrumBlockHeader subscribeHeaders() {
        requestHandler.setHeadersSubscribed(true);
        return bitcoindClient.getTip();
    }

    @JsonRpcMethod("server.ping")
    public void ping() throws BitcoindIOException {
        try {
            bitcoindClient.getBitcoindService().uptime();
        } catch(IllegalStateException e) {
            throw new BitcoindIOException(e);
        }
    }

    @JsonRpcMethod("blockchain.scripthash.subscribe")
    public String subscribeScriptHash(@JsonRpcParam("scripthash") String scriptHash) {
        requestHandler.subscribeScriptHash(scriptHash);
        return bitcoindClient.getStore().getStatus(scriptHash);
    }

    @JsonRpcMethod("blockchain.scripthash.get_history")
    public Collection<TxEntry> getHistory(@JsonRpcParam("scripthash") String scriptHash) {
        return bitcoindClient.getStore().getHistory(scriptHash);
    }

    @JsonRpcMethod("blockchain.block.header")
    public String getBlockHeader(@JsonRpcParam("height") int height) throws BitcoindIOException, BlockNotFoundException {
        try {
            String blockHash = bitcoindClient.getStore().getBlockHash(height);
            if(blockHash == null) {
                blockHash = bitcoindClient.getBitcoindService().getBlockHash(height);
            }

            return bitcoindClient.getBitcoindService().getBlockHeader(blockHash, false);
        } catch(JsonRpcException e) {
            throw new BlockNotFoundException(e.getErrorMessage());
        } catch(IllegalStateException e) {
            throw new BitcoindIOException(e);
        }
    }

    @JsonRpcMethod("blockchain.transaction.get")
    public Object getTransaction(@JsonRpcParam("tx_hash") String tx_hash, @JsonRpcParam("verbose") @JsonRpcOptional boolean verbose) throws BitcoindIOException, TransactionNotFoundException {
        try {
            return bitcoindClient.getBitcoindService().getRawTransaction(tx_hash, verbose);
        } catch(JsonRpcException e) {
            throw new TransactionNotFoundException(e.getErrorMessage());
        } catch(IllegalStateException e) {
            throw new BitcoindIOException(e);
        }
    }

    @JsonRpcMethod("blockchain.transaction.broadcast")
    public String broadcastTransaction(@JsonRpcParam("raw_tx") String rawTx) throws BitcoindIOException, BroadcastFailedException {
        try {
            return bitcoindClient.getBitcoindService().sendRawTransaction(rawTx, 0d);
        } catch(JsonRpcException e) {
            throw new BroadcastFailedException(e.getErrorMessage());
        } catch(IllegalStateException e) {
            throw new BitcoindIOException(e);
        }
    }

    private static class VsizeFeerate implements Comparable<VsizeFeerate> {
        private final int vsize;
        private final double feerate;

        public VsizeFeerate(int vsize, double fee) {
            this.vsize = vsize;
            this.feerate = fee / vsize * 100000000;
        }

        @Override
        public int compareTo(VsizeFeerate o) {
            return Double.compare(o.feerate, feerate);
        }
    }
}
