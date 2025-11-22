package com.sparrowwallet.sparrow.net;

import com.sparrowwallet.drongo.Network;
import com.sparrowwallet.drongo.Utils;
import com.sparrowwallet.drongo.protocol.Sha256Hash;
import com.sparrowwallet.drongo.wallet.BlockTransaction;
import com.sparrowwallet.drongo.wallet.BlockTransactionHash;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.BlockSummary;
import org.girod.javafx.svgimage.SVGImage;
import org.girod.javafx.svgimage.SVGLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.util.*;

public enum FeeRatesSource {
    ELECTRUM_SERVER("Server", false) {
        @Override
        public Map<Integer, Double> getBlockTargetFeeRates(Map<Integer, Double> defaultblockTargetFeeRates) {
            return Collections.emptyMap();
        }

        @Override
        public boolean supportsNetwork(Network network) {
            return true;
        }
    },
    MEMPOOL_SPACE("mempool.space", true) {
        @Override
        public Map<Integer, Double> getBlockTargetFeeRates(Map<Integer, Double> defaultblockTargetFeeRates) {
            String url = getApiUrl() + "v1/fees/recommended";
            return getThreeTierFeeRates(this, defaultblockTargetFeeRates, url);
        }

        @Override
        public Double getNextBlockMedianFeeRate() throws Exception {
            String url = getApiUrl() + "v1/fees/mempool-blocks";
            return requestNextBlockMedianFeeRate(this, url);
        }

        @Override
        public BlockSummary getBlockSummary(Sha256Hash blockId) throws Exception {
            String url = getApiUrl() + "v1/block/" + Utils.bytesToHex(blockId.getReversedBytes());
            return requestBlockSummary(this, url);
        }

        @Override
        public Map<Integer, BlockSummary> getRecentBlockSummaries() throws Exception {
            String url = getApiUrl() + "v1/blocks";
            return requestBlockSummaries(this, url);
        }

        @Override
        public List<BlockTransactionHash> getRecentMempoolTransactions() throws Exception {
            String url = getApiUrl() + "mempool/recent";
            return requestRecentMempoolTransactions(this, url);
        }

        private String getApiUrl() {
            String url = AppServices.isUsingProxy() ? "http://mempoolhqx4isw62xs7abwphsq7ldayuidyx2v2oethdhhj6mlo2r6ad.onion/api/" : "https://mempool.space/api/";
            if(Network.get() != Network.MAINNET && supportsNetwork(Network.get())) {
                url = url.replace("/api/", "/" + Network.get().getName() + "/api/");
            }
            return url;
        }

        @Override
        public boolean supportsNetwork(Network network) {
            return network == Network.MAINNET || network == Network.TESTNET || network == Network.TESTNET4 || network == Network.SIGNET;
        }
    },
    BLOCK_AUGUR("block.xyz Augur", true) {
        /*
            https://engineering.block.xyz/blog/augur-an-open-source-bitcoin-fee-estimation-library
         */
        @Override
        public Map<Integer, Double> getBlockTargetFeeRates(Map<Integer, Double> defaultblockTargetFeeRates) {
            String url = "https://pricing.bitcoin.block.xyz/fees";
            return getThreeTierFeeRates(this, defaultblockTargetFeeRates, url);
        }

        @Override
        public boolean supportsNetwork(Network network) {
            return network == Network.MAINNET;
        }

        @Override
        protected ThreeTierRates getThreeTierRates(String url, HttpClientService httpClientService) throws Exception {
            BlockAugurRates rates = httpClientService.requestJson(url, BlockAugurRates.class, null);
            if(rates.estimates == null) {
                throw new Exception("Invalid response from " + url);
            }
            return rates.getThreeTierRates();
        }
    },
    BITCOINFEES_EARN_COM("bitcoinfees.earn.com", true) {
        @Override
        public Map<Integer, Double> getBlockTargetFeeRates(Map<Integer, Double> defaultblockTargetFeeRates) {
            String url = "https://bitcoinfees.earn.com/api/v1/fees/recommended";
            return getThreeTierFeeRates(this, defaultblockTargetFeeRates, url);
        }

        @Override
        public boolean supportsNetwork(Network network) {
            return network == Network.MAINNET;
        }
    },
    MINIMUM("Minimum (1 sat/vB)", false) {
        @Override
        public Map<Integer, Double> getBlockTargetFeeRates(Map<Integer, Double> defaultblockTargetFeeRates) {
            Map<Integer, Double> blockTargetFeeRates = new LinkedHashMap<>();
            for(Integer blockTarget : defaultblockTargetFeeRates.keySet()) {
                blockTargetFeeRates.put(blockTarget, 1.0);
            }

            return blockTargetFeeRates;
        }

        @Override
        public boolean supportsNetwork(Network network) {
            return true;
        }
    },
    OXT_ME("oxt.me", true) {
        @Override
        public Map<Integer, Double> getBlockTargetFeeRates(Map<Integer, Double> defaultblockTargetFeeRates) {
            String url = AppServices.isUsingProxy() ? "http://oxtwshnfyktikbflierkwcxxksbonl6v73l5so5zky7ur72w52tktkid.onion/stats/global/mempool" : "https://api.oxt.me/stats/global/mempool";
            return getThreeTierFeeRates(this, defaultblockTargetFeeRates, url);
        }

        @Override
        public boolean supportsNetwork(Network network) {
            return network == Network.MAINNET;
        }

        @Override
        protected ThreeTierRates getThreeTierRates(String url, HttpClientService httpClientService) throws Exception {
            OxtRates oxtRates = httpClientService.requestJson(url, OxtRates.class, null);
            if(oxtRates.data == null || oxtRates.data.length < 1) {
                throw new Exception("Invalid response from " + url);
            }
            return oxtRates.data[0].getThreeTierRates();
        }
    };

    private static final Logger log = LoggerFactory.getLogger(FeeRatesSource.class);
    public static final int BLOCKS_IN_HALF_HOUR = 3;
    public static final int BLOCKS_IN_HOUR = 6;
    public static final int BLOCKS_IN_TWO_HOURS = 12;

    private final String name;
    private final boolean external;

    FeeRatesSource(String name, boolean external) {
        this.name = name;
        this.external = external;
    }

    public abstract Map<Integer, Double> getBlockTargetFeeRates(Map<Integer, Double> defaultblockTargetFeeRates);

    public Double getNextBlockMedianFeeRate() throws Exception {
        throw new UnsupportedOperationException(name + " does not support retrieving the next block median fee rate");
    }

    public BlockSummary getBlockSummary(Sha256Hash blockId) throws Exception {
        throw new UnsupportedOperationException(name + " does not support block summaries");
    }

    public Map<Integer, BlockSummary> getRecentBlockSummaries() throws Exception {
        throw new UnsupportedOperationException(name + " does not support block summaries");
    }

    public List<BlockTransactionHash> getRecentMempoolTransactions() throws Exception {
        throw new UnsupportedOperationException(name + " does not support recent mempool transactions");
    }

    public abstract boolean supportsNetwork(Network network);

    public String getName() {
        return name;
    }

    public boolean isExternal() {
        return external;
    }

    private static Map<Integer, Double> getThreeTierFeeRates(FeeRatesSource feeRatesSource, Map<Integer, Double> defaultblockTargetFeeRates, String url) {
        if(log.isInfoEnabled()) {
            log.info("Requesting fee rates from " + url);
        }

        Map<Integer, Double> blockTargetFeeRates = new LinkedHashMap<>();
        HttpClientService httpClientService = AppServices.getHttpClientService();
        try {
            ThreeTierRates threeTierRates = feeRatesSource.getThreeTierRates(url, httpClientService);
            Double lastRate = null;
            for(Integer blockTarget : defaultblockTargetFeeRates.keySet()) {
                if(blockTarget < BLOCKS_IN_HALF_HOUR) {
                    blockTargetFeeRates.put(blockTarget, threeTierRates.fastestFee);
                } else if(blockTarget < BLOCKS_IN_HOUR) {
                    blockTargetFeeRates.put(blockTarget, threeTierRates.halfHourFee);
                } else if(blockTarget < BLOCKS_IN_TWO_HOURS || defaultblockTargetFeeRates.get(blockTarget) > threeTierRates.hourFee) {
                    blockTargetFeeRates.put(blockTarget, threeTierRates.hourFee);
                } else if(threeTierRates.minimumFee != null && defaultblockTargetFeeRates.get(blockTarget) < threeTierRates.minimumFee) {
                    blockTargetFeeRates.put(blockTarget, threeTierRates.minimumFee + (threeTierRates.hourFee > threeTierRates.minimumFee ? threeTierRates.hourFee * 0.2 : 0.0));
                } else {
                    blockTargetFeeRates.put(blockTarget, defaultblockTargetFeeRates.get(blockTarget));
                }

                if(lastRate != null) {
                    blockTargetFeeRates.put(blockTarget, Math.min(lastRate, blockTargetFeeRates.get(blockTarget)));
                }
                lastRate = blockTargetFeeRates.get(blockTarget);
            }

            if(threeTierRates.minimumFee != null) {
                blockTargetFeeRates.put(Integer.MAX_VALUE, threeTierRates.minimumFee);
            }
        } catch (Exception e) {
            if(log.isDebugEnabled()) {
                log.warn("Error retrieving recommended fee rates from " + url, e);
            } else {
                log.warn("Error retrieving recommended fee rates from " + url + " (" + e.getMessage() + ")");
            }
        }

        return blockTargetFeeRates;
    }

    protected ThreeTierRates getThreeTierRates(String url, HttpClientService httpClientService) throws Exception {
        return httpClientService.requestJson(url, ThreeTierRates.class, null);
    }

    protected static Double requestNextBlockMedianFeeRate(FeeRatesSource feeRatesSource, String url) throws Exception {
        if(log.isInfoEnabled()) {
            log.info("Requesting next block median fee rate from " + url);
        }

        HttpClientService httpClientService = AppServices.getHttpClientService();
        try {
            MempoolBlock[] mempoolBlocks = feeRatesSource.requestMempoolBlocks(url, httpClientService);
            return mempoolBlocks.length > 0 ? mempoolBlocks[0].medianFee : null;
        } catch (Exception e) {
            if(log.isDebugEnabled()) {
                log.warn("Error retrieving next block median fee rate from " + url, e);
            } else {
                log.warn("Error retrieving next block median fee rate from " + url + " (" + e.getMessage() + ")");
            }

            throw e;
        }
    }

    protected MempoolBlock[] requestMempoolBlocks(String url, HttpClientService httpClientService) throws Exception {
        return httpClientService.requestJson(url, MempoolBlock[].class, null);
    }

    protected static BlockSummary requestBlockSummary(FeeRatesSource feeRatesSource, String url) throws Exception {
        if(log.isInfoEnabled()) {
            log.info("Requesting block summary from " + url);
        }

        HttpClientService httpClientService = AppServices.getHttpClientService();
        try {
            MempoolBlockSummary mempoolBlockSummary = feeRatesSource.requestBlockSummary(url, httpClientService);
            return mempoolBlockSummary.toBlockSummary();
        } catch (Exception e) {
            if(log.isDebugEnabled()) {
                log.warn("Error retrieving block summary from " + url, e);
            } else {
                log.warn("Error retrieving block summary from " + url + " (" + e.getMessage() + ")");
            }

            throw e;
        }
    }

    protected MempoolBlockSummary requestBlockSummary(String url, HttpClientService httpClientService) throws Exception {
        return httpClientService.requestJson(url, MempoolBlockSummary.class, null);
    }

    protected static Map<Integer, BlockSummary> requestBlockSummaries(FeeRatesSource feeRatesSource, String url) throws Exception {
        if(log.isInfoEnabled()) {
            log.info("Requesting block summaries from " + url);
        }

        Map<Integer, BlockSummary> blockSummaryMap = new LinkedHashMap<>();
        HttpClientService httpClientService = AppServices.getHttpClientService();
        try {
            MempoolBlockSummary[] blockSummaries = feeRatesSource.requestBlockSummaries(url, httpClientService);
            for(MempoolBlockSummary blockSummary : blockSummaries) {
                if(blockSummary.height != null) {
                    blockSummaryMap.put(blockSummary.height, blockSummary.toBlockSummary());
                }
            }
            return blockSummaryMap;
        } catch (Exception e) {
            if(log.isDebugEnabled()) {
                log.warn("Error retrieving block summaries from " + url, e);
            } else {
                log.warn("Error retrieving block summaries from " + url + " (" + e.getMessage() + ")");
            }

            throw e;
        }
    }

    protected MempoolBlockSummary[] requestBlockSummaries(String url, HttpClientService httpClientService) throws Exception {
        return httpClientService.requestJson(url, MempoolBlockSummary[].class, null);
    }

    protected List<BlockTransactionHash> requestRecentMempoolTransactions(FeeRatesSource feeRatesSource, String url) throws Exception {
        HttpClientService httpClientService = AppServices.getHttpClientService();
        try {
            MempoolRecentTransaction[] recentTransactions = feeRatesSource.requestRecentMempoolTransactions(url, httpClientService);
            return Arrays.stream(recentTransactions).sorted().map(tx -> (BlockTransactionHash)new BlockTransaction(tx.txid, 0, null, tx.fee, null)).toList();
        } catch (Exception e) {
            if(log.isDebugEnabled()) {
                log.warn("Error retrieving recent mempool transactions from " + url, e);
            } else {
                log.warn("Error retrieving recent mempool from " + url + " (" + e.getMessage() + ")");
            }

            throw e;
        }
    }

    protected MempoolRecentTransaction[] requestRecentMempoolTransactions(String url, HttpClientService httpClientService) throws Exception {
        return httpClientService.requestJson(url, MempoolRecentTransaction[].class, null);
    }

    @Override
    public String toString() {
        return name;
    }

    public String getDescription() {
        return switch(this) {
            case ELECTRUM_SERVER -> "server";
            case MINIMUM -> "settings";
            default -> getName().toLowerCase(Locale.ROOT);
        };
    }

    public SVGImage getSVGImage() {
        try {
            URL url = AppServices.class.getResource("/image/feeratesource/" + getDescription() + "-icon.svg");
            if(url != null) {
                return SVGLoader.load(url);
            }
        } catch(Exception e) {
            log.error("Could not load fee rates source image for " + name);
        }

        return null;
    }

    protected record ThreeTierRates(Double fastestFee, Double halfHourFee, Double hourFee, Double minimumFee) {}

    private record OxtRates(OxtRatesData[] data) {}

    private record OxtRatesData(Double recommended_fee_099, Double recommended_fee_090, Double recommended_fee_050) {
        public ThreeTierRates getThreeTierRates() {
            return new ThreeTierRates(recommended_fee_099/1000, recommended_fee_090/1000, recommended_fee_050/1000, null);
        }
    }

    private record BlockAugurRates(Map<String, BlockAugurEstimate> estimates) {
        public ThreeTierRates getThreeTierRates() {
            // see https://engineering.block.xyz/blog/augur-an-open-source-bitcoin-fee-estimation-library
            //
            // fastestFee: 95% confidence at 3 blocks
            // halfHourFee: 80% confidence at 3 blocks
            // hourFee: 80% confidence at 6 blocks
            // minimumFee: 80% confidence at 144 blocks
            Double fastestFee = getFeeRate("3", "0.95");
            Double halfHourFee = getFeeRate("3", "0.80");
            Double hourFee = getFeeRate("6", "0.80");
            Double minimumFee = getFeeRate("144", "0.80");
            return new ThreeTierRates(fastestFee, halfHourFee, hourFee, minimumFee);
        }

        private Double getFeeRate(String blocks, String probability) {
            BlockAugurEstimate estimate = estimates.get(blocks);
            if(estimate != null && estimate.probabilities != null) {
                BlockAugurFeeRate feeRate = estimate.probabilities.get(probability);
                if(feeRate != null) {
                    return feeRate.fee_rate;
                }
            }
            return 1.0;
        }
    }

    private record BlockAugurEstimate(Map<String, BlockAugurFeeRate> probabilities) {}

    private record BlockAugurFeeRate(Double fee_rate) {}

    protected record MempoolBlock(Integer nTx, Double medianFee) {}

    protected record MempoolBlockSummary(String id, Integer height, Long timestamp, Integer tx_count, Integer weight, MempoolBlockSummaryExtras extras) {
        public Double getMedianFee() {
            return extras == null ? null : extras.medianFee();
        }

        public BlockSummary toBlockSummary() {
            if(height == null || timestamp == null) {
                throw new IllegalStateException("Height = " + height + ", timestamp = " + timestamp + ": both must be specified");
            }
            return new BlockSummary(height, new Date(timestamp * 1000), getMedianFee(), tx_count, weight);
        }
    }

    private record MempoolBlockSummaryExtras(Double medianFee) {}

    protected record MempoolRecentTransaction(Sha256Hash txid, Long fee, Long vsize) implements Comparable<MempoolRecentTransaction> {
        private Double getFeeRate() {
            return fee == null || vsize == null ? 0.0d : (double)fee / vsize;
        }

        @Override
        public int compareTo(MempoolRecentTransaction o) {
            return Double.compare(o.getFeeRate(), getFeeRate());
        }
    }
}
