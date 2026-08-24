package com.sparrowwallet.sparrow.net;

import com.github.arteam.simplejsonrpc.client.Transport;
import com.github.arteam.simplejsonrpc.client.exception.JsonRpcBatchException;
import com.github.arteam.simplejsonrpc.client.exception.JsonRpcException;
import com.sparrowwallet.drongo.protocol.HeaderChainState;
import com.sparrowwallet.drongo.protocol.VerificationException;
import com.sparrowwallet.drongo.wallet.BlockTransactionHash;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.AppServices;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface ElectrumServerRpc {
    /** The JSON-RPC standard code for a method the server does not implement, which bitcoind returns as well as Electrum servers. */
    int METHOD_NOT_FOUND = -32601;

    /**
     * Checks the connection is alive with server.ping, throwing ElectrumServerRpcException if the server does not answer.
     */
    void ping(Transport transport);

    /**
     * Negotiates the protocol with server.version, returning the server's software name followed by the protocol version agreed on, which may be
     * lower than any of those offered.
     */
    List<String> getServerVersion(Transport transport, String clientName, String[] supportedVersions);

    /**
     * Retrieves server.features. Not every server implements it.
     */
    ServerFeatures getServerFeatures(Transport transport);

    /**
     * Retrieves the server's banner text for display, or throws ElectrumServerRpcException if the server does not provide one.
     */
    String getServerBanner(Transport transport);

    /**
     * Subscribes to the chain tip with blockchain.headers.subscribe, returning the tip as it stands. Later tips arrive as notifications rather than
     * as returns from this method.
     */
    BlockHeaderTip subscribeBlockHeaders(Transport transport);

    /**
     * Retrieves the confirmed and mempool history of each script hash. Both the argument and the result are keyed by the caller's derivation path
     * rather than by script hash, so that the wallet node a history belongs to survives the round trip.
     * Where failOnError is false, a path the server returned an error for carries a single ScriptHashTx.ERROR_TX rather than being absent; where it is
     * true, one error fails the whole call.
     */
    Map<String, ScriptHashTx[]> getScriptHashHistory(Transport transport, Wallet wallet, Map<String, String> pathScriptHashes, boolean failOnError);

    /**
     * Retrieves the mempool transactions of each script hash, keyed by derivation path and handling errors as getScriptHashHistory does.
     */
    Map<String, ScriptHashTx[]> getScriptHashMempool(Transport transport, Wallet wallet, Map<String, String> pathScriptHashes, boolean failOnError);

    /**
     * Subscribes to each script hash, returning the current status of each keyed by the caller's derivation path. A status is null where the script
     * hash has no history. Any error fails the whole call: subscribing to some but not all of a wallet's script hashes would leave its view of itself
     * silently out of date.
     */
    Map<String, String> subscribeScriptHashes(Transport transport, Wallet wallet, Map<String, String> pathScriptHashes);

    /**
     * Unsubscribes from each script hash, returning those the server answered for, mapped to whether it considered them subscribed. A script hash the
     * server did not answer for is absent. This never throws: an unsubscribe that fails costs a redundant subscription rather than correctness.
     */
    Map<String, Boolean> unsubscribeScriptHashes(Transport transport, Set<String> scriptHashes);

    /**
     * Subscribes the given scan key to silent payments from the given start, which is read as a block height below Transaction.MAX_BLOCK_LOCKTIME and
     * as a unix timestamp at or above it. The returned subscription carries the start height the server actually adopted, which may cover more than
     * was asked for, and which the caller records so that a later subscription can tell whether wider coverage is still needed.
     */
    SilentPaymentsSubscription subscribeSilentPayments(Transport transport, Wallet wallet, String scanPrivKeyHex, String spendPubKeyHex, Object start, int[] labels);

    /**
     * Ends the silent payments subscription for the given scan key, returning the server's response.
     */
    String unsubscribeSilentPayments(Transport transport, String scanPrivKeyHex, String spendPubKeyHex);

    /**
     * Retrieves the serialized block header at each height. A height the server returned an error for is absent from the result rather than failing
     * the call.
     */
    Map<Integer, String> getBlockHeaders(Transport transport, Wallet wallet, Set<Integer> blockHeights);

    /**
     * Retrieves the statistics of each block, omitting the heights the server returned an error for. Not every server implements this call.
     */
    Map<Integer, BlockStats> getBlockStats(Transport transport, Set<Integer> blockHeights);

    /**
     * Retrieves each transaction as serialized hex. A txid the server returned an error for is present with a value of Sha256Hash.ZERO_HASH as a
     * string, not absent, so that the caller can tell a transaction the server would not supply from one it was never asked for.
     */
    Map<String, String> getTransactions(Transport transport, Wallet wallet, Set<String> txids);

    /**
     * Retrieves each transaction with the block information the server holds for it. A txid the server does not know is absent from the result, which
     * is a valid state for a transaction that has not been broadcast yet.
     * An entry whose blockhash is Sha256Hash.ZERO_HASH is incomplete: the server did not supply the block information. Where scriptHash is supplied it
     * allows the height of such an entry to be recovered, and it may be null.
     */
    Map<String, VerboseTransaction> getVerboseTransactions(Transport transport, Set<String> txids, String scriptHash);

    /**
     * Retrieves the merkle inclusion proof for each of the given transactions at the height it is claimed to be confirmed at.
     * The result is keyed by the exact pair as "txid:height", since the same transaction may legitimately be requested at two heights in one call,
     * and carries TransactionMerkleProof.ERROR_PROOF for every pair the server returned an error for.
     */
    Map<String, TransactionMerkleProof> getTransactionMerkleProofs(Transport transport, Wallet wallet, Collection<BlockTransactionHash> references);

    /**
     * Retrieves a run of consecutive block headers, throwing VerificationException if the response is malformed or short of the server's own tip.
     */
    BlockHeaders getBlockHeadersChunk(Transport transport, int startHeight, int count);

    /**
     * Retrieves several runs of consecutive block headers, keyed by start height. A range the server errors on, or whose response fails the
     * checks getBlockHeadersChunk applies, is omitted from the result rather than failing the others.
     */
    Map<Integer, BlockHeaders> getBlockHeadersChunks(Transport transport, Map<Integer, Integer> startHeightCounts);

    /**
     * Retrieves the fee rate in BTC/kB estimated to confirm within each number of blocks. Targets beyond the number the server will estimate for are
     * answered with the lowest rate already seen rather than being absent, so the caller always receives a rate for every target it asked about.
     */
    Map<Integer, Double> getFeeEstimates(Transport transport, List<Integer> targetBlocks);

    /**
     * Retrieves the mempool's fee rate histogram, mapping fee rate in sats/vB to the virtual size of the transactions paying at least it, ascending by
     * fee rate. Buckets at a fee rate of zero are dropped.
     */
    Map<Double, Long> getFeeRateHistogram(Transport transport);

    /**
     * Retrieves the minimum fee rate in BTC/kB the server's node will relay a transaction at.
     */
    Double getMinimumRelayFee(Transport transport);

    /**
     * Broadcasts the given serialized transaction, returning its txid. Where the server rejects it, the ElectrumServerRpcException carries the server's
     * own error message, which is shown to the user as the reason.
     */
    String broadcastTransaction(Transport transport, String txHex);

    /**
     * The last JSON-RPC request id used, so that an implementation replacing this one can continue the sequence rather than reusing ids.
     */
    long getIdCounterValue();

    /** Whether every error in a batch reports the method as not found, which is a property of the server rather than of any one request. */
    static boolean isMethodNotFound(JsonRpcBatchException e) {
        return !e.getErrors().isEmpty() && e.getErrors().values().stream().allMatch(error -> error.getCode() == METHOD_NOT_FOUND);
    }

    static boolean isMethodNotFound(JsonRpcException e) {
        return e.getErrorMessage() != null && e.getErrorMessage().getCode() == METHOD_NOT_FOUND;
    }

    /**
     * Applies Electrum's checks to a blockchain.block.headers response. A server returning at most a partial difficulty period cannot serve the
     * header sync at all, and a response shorter than requested is only explicable by the server having reached its own announced tip.
     */
    static BlockHeaders checkBlockHeaders(BlockHeaders blockHeaders, int startHeight, int count) throws VerificationException {
        return checkBlockHeaders(blockHeaders, startHeight, count, AppServices.getCurrentBlockHeight());
    }

    static BlockHeaders checkBlockHeaders(BlockHeaders blockHeaders, int startHeight, int count, Integer tip) throws VerificationException {
        if(blockHeaders == null || blockHeaders.hex == null || blockHeaders.count < 0) {
            throw new VerificationException("Malformed response to a request for " + count + " block headers from height " + startHeight);
        }
        if(blockHeaders.max < HeaderChainState.RETARGET_INTERVAL) {
            throw new VerificationException("Server returns at most " + blockHeaders.max + " block headers per request, too few to cover a difficulty period");
        }
        if(blockHeaders.count > count) {
            throw new VerificationException("Requested " + count + " block headers from height " + startHeight + " but the server returned " + blockHeaders.count);
        }
        if(blockHeaders.hex.length() != blockHeaders.count * BlockHeaders.HEADER_HEX_LENGTH) {
            throw new VerificationException("Response to a request for block headers from height " + startHeight + " contains " + blockHeaders.hex.length() / 2
                    + " bytes for " + blockHeaders.count + " headers");
        }

        if(blockHeaders.count < count && tip != null && startHeight + blockHeaders.count - 1 < tip) {
            throw new VerificationException("Requested " + count + " block headers from height " + startHeight + " but the server returned only " + blockHeaders.count
                    + " while announcing a tip at height " + tip);
        }

        return blockHeaders;
    }
}
