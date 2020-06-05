package com.sparrowwallet.sparrow.io;

import com.github.arteam.simplejsonrpc.client.*;
import com.github.arteam.simplejsonrpc.client.builder.BatchRequestBuilder;
import com.google.common.net.HostAndPort;
import com.sparrowwallet.drongo.KeyPurpose;
import com.sparrowwallet.drongo.Utils;
import com.sparrowwallet.drongo.protocol.*;
import com.sparrowwallet.drongo.wallet.*;
import javafx.concurrent.ScheduledService;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import org.jetbrains.annotations.NotNull;

import javax.net.SocketFactory;
import javax.net.ssl.*;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Socket;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.stream.Collectors;

public class ElectrumServer {
    private static final String[] SUPPORTED_VERSIONS = new String[]{"1.3", "1.4.2"};

    private static Transport transport;

    private static synchronized Transport getTransport() throws ServerException {
        if(transport == null) {
            try {
                String electrumServer = Config.get().getElectrumServer();
                File electrumServerCert = Config.get().getElectrumServerCert();
                String proxyServer = Config.get().getProxyServer();

                if(electrumServer == null) {
                    throw new ServerException("Electrum server URL not specified");
                }

                if(electrumServerCert != null && !electrumServerCert.exists()) {
                    throw new ServerException("Electrum server certificate file not found");
                }

                Protocol protocol = Protocol.getProtocol(electrumServer);
                if(protocol == null) {
                    throw new ServerException("Electrum server URL must start with " + Protocol.TCP.toUrlString() + " or " + Protocol.SSL.toUrlString());
                }

                HostAndPort server = protocol.getServerHostAndPort(electrumServer);

                if(Config.get().isUseProxy() && proxyServer != null && !proxyServer.isBlank()) {
                    HostAndPort proxy = HostAndPort.fromString(proxyServer);
                    if(electrumServerCert != null) {
                        transport = protocol.getTransport(server, electrumServerCert, proxy);
                    } else {
                        transport = protocol.getTransport(server, proxy);
                    }
                } else {
                    if(electrumServerCert != null) {
                        transport = protocol.getTransport(server, electrumServerCert);
                    } else {
                        transport = protocol.getTransport(server);
                    }
                }
            } catch (Exception e) {
                throw new ServerException(e);
            }
        }

        return transport;
    }

    public void ping() throws ServerException {
        JsonRpcClient client = new JsonRpcClient(getTransport());
        client.createRequest().method("server.ping").id(1).executeNullable();
    }

    public List<String> getServerVersion() throws ServerException {
        JsonRpcClient client = new JsonRpcClient(getTransport());
        return client.createRequest().returnAsList(String.class).method("server.version").id(1).param("client_name", "Sparrow").param("protocol_version", SUPPORTED_VERSIONS).execute();
    }

    public String getServerBanner() throws ServerException {
        JsonRpcClient client = new JsonRpcClient(getTransport());
        return client.createRequest().returnAs(String.class).method("server.banner").id(1).execute();
    }

    public static synchronized void closeActiveConnection() throws ServerException {
        try {
            if(transport != null) {
                Closeable closeableTransport = (Closeable)transport;
                closeableTransport.close();
                transport = null;
            }
        } catch (IOException e) {
            throw new ServerException(e);
        }
    }

    public Map<WalletNode, Set<BlockTransactionHash>> getHistory(Wallet wallet) throws ServerException {
        Map<WalletNode, Set<BlockTransactionHash>> nodeTransactionMap = new HashMap<>();
        getHistory(wallet, KeyPurpose.RECEIVE, nodeTransactionMap);
        getHistory(wallet, KeyPurpose.CHANGE, nodeTransactionMap);

        return nodeTransactionMap;
    }

    public void getHistory(Wallet wallet, KeyPurpose keyPurpose, Map<WalletNode, Set<BlockTransactionHash>> nodeTransactionMap) throws ServerException {
        getHistory(wallet, wallet.getNode(keyPurpose).getChildren(), nodeTransactionMap);
        getMempool(wallet, wallet.getNode(keyPurpose).getChildren(), nodeTransactionMap);
    }

    public void getHistory(Wallet wallet, Collection<WalletNode> nodes, Map<WalletNode, Set<BlockTransactionHash>> nodeTransactionMap) throws ServerException {
        getReferences(wallet, "blockchain.scripthash.get_history", nodes, nodeTransactionMap);
    }

    public void getMempool(Wallet wallet, Collection<WalletNode> nodes, Map<WalletNode, Set<BlockTransactionHash>> nodeTransactionMap) throws ServerException {
        getReferences(wallet, "blockchain.scripthash.get_mempool", nodes, nodeTransactionMap);
    }

    public void getReferences(Wallet wallet, String method, Collection<WalletNode> nodes, Map<WalletNode, Set<BlockTransactionHash>> nodeTransactionMap) throws ServerException {
        try {
            JsonRpcClient client = new JsonRpcClient(getTransport());
            BatchRequestBuilder<String, ScriptHashTx[]> batchRequest = client.createBatchRequest().keysType(String.class).returnType(ScriptHashTx[].class);
            for(WalletNode node : nodes) {
                batchRequest.add(node.getDerivationPath(), method, getScriptHash(wallet, node));
            }
            Map<String, ScriptHashTx[]> result = batchRequest.execute();

            for(String path : result.keySet()) {
                ScriptHashTx[] txes = result.get(path);

                Optional<WalletNode> optionalNode = nodes.stream().filter(n -> n.getDerivationPath().equals(path)).findFirst();
                if(optionalNode.isPresent()) {
                    WalletNode node = optionalNode.get();

                    Set<BlockTransactionHash> references = Arrays.stream(txes).map(ScriptHashTx::getBlockchainTransactionHash).collect(Collectors.toCollection(TreeSet::new));
                    Set<BlockTransactionHash> existingReferences = nodeTransactionMap.get(node);

                    if(existingReferences == null && !references.isEmpty()) {
                        nodeTransactionMap.put(node, references);
                    } else {
                        for(BlockTransactionHash reference : references) {
                            if(!existingReferences.add(reference)) {
                                Optional<BlockTransactionHash> optionalReference = existingReferences.stream().filter(tr -> tr.getHash().equals(reference.getHash())).findFirst();
                                if(optionalReference.isPresent()) {
                                    BlockTransactionHash existingReference = optionalReference.get();
                                    if(existingReference.getHeight() < reference.getHeight()) {
                                        existingReferences.remove(existingReference);
                                        existingReferences.add(reference);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (IllegalStateException e) {
            throw new ServerException(e.getCause());
        } catch (Exception e) {
            throw new ServerException(e);
        }
    }

    public void getReferencedTransactions(Wallet wallet, Map<WalletNode, Set<BlockTransactionHash>> nodeTransactionMap) throws ServerException {
        Set<BlockTransactionHash> references = new TreeSet<>();
        for(Set<BlockTransactionHash> nodeReferences : nodeTransactionMap.values()) {
            references.addAll(nodeReferences);
        }

        Map<Integer, BlockHeader> blockHeaderMap = getBlockHeaders(references);
        Map<Sha256Hash, BlockTransaction> transactionMap = getTransactions(references, blockHeaderMap);

        if(!transactionMap.equals(wallet.getTransactions())) {
            for(BlockTransaction blockTx : transactionMap.values()) {
                Optional<String> optionalLabel = wallet.getTransactions().values().stream().filter(oldBlTx -> oldBlTx.getHash().equals(blockTx.getHash())).map(BlockTransaction::getLabel).findFirst();
                optionalLabel.ifPresent(blockTx::setLabel);
            }

            wallet.getTransactions().clear();
            wallet.getTransactions().putAll(transactionMap);
        }
    }

    public Map<Integer, BlockHeader> getBlockHeaders(Set<BlockTransactionHash> references) throws ServerException {
        try {
            Set<Integer> blockHeights = new TreeSet<>();
            for(BlockTransactionHash reference : references) {
                blockHeights.add(reference.getHeight());
            }

            JsonRpcClient client = new JsonRpcClient(getTransport());
            BatchRequestBuilder<Integer, String> batchRequest = client.createBatchRequest().keysType(Integer.class).returnType(String.class);
            for(Integer height : blockHeights) {
                batchRequest.add(height, "blockchain.block.header", height);
            }
            Map<Integer, String> result = batchRequest.execute();

            Map<Integer, BlockHeader> blockHeaderMap = new TreeMap<>();
            for(Integer height : result.keySet()) {
                byte[] blockHeaderBytes = Utils.hexToBytes(result.get(height));
                BlockHeader blockHeader = new BlockHeader(blockHeaderBytes);
                blockHeaderMap.put(height, blockHeader);
                blockHeights.remove(height);
            }

            if(!blockHeights.isEmpty()) {
                throw new IllegalStateException("Could not retrieve blocks " + blockHeights);
            }

            return blockHeaderMap;
        } catch (IllegalStateException e) {
            throw new ServerException(e.getCause());
        } catch (Exception e) {
            throw new ServerException(e);
        }
    }

    public Map<Sha256Hash, BlockTransaction> getTransactions(Set<BlockTransactionHash> references, Map<Integer, BlockHeader> blockHeaderMap) throws ServerException {
        try {
            Set<BlockTransactionHash> checkReferences = new TreeSet<>(references);

            JsonRpcClient client = new JsonRpcClient(getTransport());
            BatchRequestBuilder<String, String> batchRequest = client.createBatchRequest().keysType(String.class).returnType(String.class);
            for(BlockTransactionHash reference : references) {
                batchRequest.add(reference.getHashAsString(), "blockchain.transaction.get", reference.getHashAsString());
            }
            Map<String, String> result = batchRequest.execute();

            Map<Sha256Hash, BlockTransaction> transactionMap = new HashMap<>();
            for(String txid : result.keySet()) {
                Sha256Hash hash = Sha256Hash.wrap(txid);
                byte[] rawtx = Utils.hexToBytes(result.get(txid));
                Transaction transaction = new Transaction(rawtx);

                Optional<BlockTransactionHash> optionalReference = references.stream().filter(reference -> reference.getHash().equals(hash)).findFirst();
                if(optionalReference.isEmpty()) {
                    throw new IllegalStateException("Returned transaction " + hash.toString() + " that was not requested");
                }
                BlockTransactionHash reference = optionalReference.get();

                BlockHeader blockHeader = blockHeaderMap.get(reference.getHeight());
                if(blockHeader == null) {
                    throw new IllegalStateException("Block header at height " + reference.getHeight() + " not retrieved");
                }

                BlockTransaction blockchainTransaction = new BlockTransaction(reference.getHash(), reference.getHeight(), blockHeader.getTimeAsDate(), reference.getFee(), transaction);

                transactionMap.put(hash, blockchainTransaction);
                checkReferences.remove(reference);
            }

            if(!checkReferences.isEmpty()) {
                throw new IllegalStateException("Could not retrieve transactions " + checkReferences);
            }

            return transactionMap;
        } catch (IllegalStateException e) {
            throw new ServerException(e.getCause());
        } catch (Exception e) {
            throw new ServerException(e);
        }
    }

    public void calculateNodeHistory(Wallet wallet, Map<WalletNode, Set<BlockTransactionHash>> nodeTransactionMap) {
        for(WalletNode node : nodeTransactionMap.keySet()) {
            calculateNodeHistory(wallet, nodeTransactionMap, node);
        }
    }

    public void calculateNodeHistory(Wallet wallet, Map<WalletNode, Set<BlockTransactionHash>> nodeTransactionMap, WalletNode node) {
        Set<BlockTransactionHashIndex> transactionOutputs = new TreeSet<>();

        Script nodeScript = wallet.getOutputScript(node);
        Set<BlockTransactionHash> history = nodeTransactionMap.get(node);
        for(BlockTransactionHash reference : history) {
            BlockTransaction blockTransaction = wallet.getTransactions().get(reference.getHash());
            if (blockTransaction == null) {
                throw new IllegalStateException("Could not retrieve transaction for hash " + reference.getHashAsString());
            }
            Transaction transaction = blockTransaction.getTransaction();

            for (int outputIndex = 0; outputIndex < transaction.getOutputs().size(); outputIndex++) {
                TransactionOutput output = transaction.getOutputs().get(outputIndex);
                if (output.getScript().equals(nodeScript)) {
                    BlockTransactionHashIndex receivingTXO = new BlockTransactionHashIndex(reference.getHash(), reference.getHeight(), blockTransaction.getDate(), reference.getFee(), output.getIndex(), output.getValue());
                    transactionOutputs.add(receivingTXO);
                }
            }
        }

        for(BlockTransactionHash reference : history) {
            BlockTransaction blockTransaction = wallet.getTransactions().get(reference.getHash());
            if (blockTransaction == null) {
                throw new IllegalStateException("Could not retrieve transaction for hash " + reference.getHashAsString());
            }
            Transaction transaction = blockTransaction.getTransaction();

            for(int inputIndex = 0; inputIndex < transaction.getInputs().size(); inputIndex++) {
                TransactionInput input = transaction.getInputs().get(inputIndex);
                Sha256Hash previousHash = input.getOutpoint().getHash();
                BlockTransaction previousTransaction = wallet.getTransactions().get(previousHash);
                if(previousTransaction == null) {
                    //No referenced transaction found, cannot check if spends from wallet
                    //This is fine so long as all referenced transactions have been returned, in which case this refers to a transaction that does not affect this wallet
                    continue;
                }

                Optional<BlockTransactionHash> optionalTxHash = history.stream().filter(txHash -> txHash.getHash().equals(previousHash)).findFirst();
                if(optionalTxHash.isEmpty()) {
                    //No previous transaction history found, cannot check if spends from wallet
                    //This is fine so long as all referenced transactions have been returned, in which case this refers to a transaction that does not affect this wallet node
                    continue;
                }

                BlockTransactionHash spentTxHash = optionalTxHash.get();
                TransactionOutput spentOutput = previousTransaction.getTransaction().getOutputs().get((int)input.getOutpoint().getIndex());
                if(spentOutput.getScript().equals(nodeScript)) {
                    BlockTransactionHashIndex spendingTXI = new BlockTransactionHashIndex(reference.getHash(), reference.getHeight(), blockTransaction.getDate(), reference.getFee(), inputIndex, spentOutput.getValue());
                    BlockTransactionHashIndex spentTXO = new BlockTransactionHashIndex(spentTxHash.getHash(), spentTxHash.getHeight(), previousTransaction.getDate(), spentTxHash.getFee(), spentOutput.getIndex(), spentOutput.getValue(), spendingTXI);

                    Optional<BlockTransactionHashIndex> optionalReference = transactionOutputs.stream().filter(receivedTXO -> receivedTXO.equals(spentTXO)).findFirst();
                    if(optionalReference.isEmpty()) {
                        throw new IllegalStateException("Found spent transaction output " + spentTXO + " but no record of receiving it");
                    }

                    BlockTransactionHashIndex receivedTXO = optionalReference.get();
                    receivedTXO.setSpentBy(spendingTXI);
                }
            }
        }

        if(!transactionOutputs.equals(node.getTransactionOutputs())) {
            for(BlockTransactionHashIndex txo : transactionOutputs) {
                Optional<String> optionalLabel = node.getTransactionOutputs().stream().filter(oldTxo -> oldTxo.getHash().equals(txo.getHash()) && oldTxo.getIndex() == txo.getIndex()).map(BlockTransactionHash::getLabel).findFirst();
                optionalLabel.ifPresent(txo::setLabel);
            }

            node.getTransactionOutputs().clear();
            node.getTransactionOutputs().addAll(transactionOutputs);
        }
    }

    private String getScriptHash(Wallet wallet, WalletNode node) {
        byte[] hash = Sha256Hash.hash(wallet.getOutputScript(node).getProgram());
        byte[] reversed = Utils.reverseBytes(hash);
        return Utils.bytesToHex(reversed);
    }

    private static class ScriptHashTx {
        public int height;
        public String tx_hash;
        public long fee;

        public BlockTransactionHash getBlockchainTransactionHash() {
            Sha256Hash hash = Sha256Hash.wrap(tx_hash);
            return new BlockTransaction(hash, height, null, fee, null);
        }

        @Override
        public String toString() {
            return "ScriptHashTx{height=" + height + ", tx_hash='" + tx_hash + '\'' + ", fee=" + fee + '}';
        }
    }

    public static class TcpTransport implements Transport, Closeable {
        public static final int DEFAULT_PORT = 50001;

        protected final HostAndPort server;
        protected final SocketFactory socketFactory;

        private Socket socket;

        public TcpTransport(HostAndPort server) {
            this.server = server;
            this.socketFactory = SocketFactory.getDefault();
        }

        @Override
        public @NotNull String pass(@NotNull String request) throws IOException {
            if(socket == null) {
                socket = createSocket();
            }

            try {
                writeRequest(socket, request);
            } catch (IOException e) {
                socket = createSocket();
                writeRequest(socket, request);
            }

            return readResponse(socket);
        }

        private void writeRequest(Socket socket, String request) throws IOException {
            PrintWriter out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())));
            out.println(request);
            out.flush();
        }

        private String readResponse(Socket socket) throws IOException {
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            String response = in.readLine();

            if(response == null) {
                throw new IOException("Could not connect to server at " + Config.get().getElectrumServer());
            }

            return response;
        }

        protected Socket createSocket() throws IOException {
            return socketFactory.createSocket(server.getHost(), server.getPortOrDefault(DEFAULT_PORT));
        }

        @Override
        public void close() throws IOException {
            if(socket != null) {
                socket.close();
            }
        }
    }

    public static class TcpOverTlsTransport extends TcpTransport {
        public static final int DEFAULT_PORT = 50002;

        protected final SSLSocketFactory sslSocketFactory;

        public TcpOverTlsTransport(HostAndPort server) throws NoSuchAlgorithmException, KeyManagementException {
            super(server);

            TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }
                    public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                    public void checkServerTrusted(X509Certificate[] certs, String authType) {}
                }
            };

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAllCerts, new SecureRandom());

            this.sslSocketFactory = sslContext.getSocketFactory();
        }

        public TcpOverTlsTransport(HostAndPort server, File crtFile) throws IOException, CertificateException, NoSuchAlgorithmException, KeyStoreException, KeyManagementException {
            super(server);

            Certificate certificate = CertificateFactory.getInstance("X.509").generateCertificate(new FileInputStream(crtFile));

            KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            keyStore.load(null, null);
            keyStore.setCertificateEntry("electrumx", certificate);

            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init(keyStore);

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustManagerFactory.getTrustManagers(), null);

            sslSocketFactory = sslContext.getSocketFactory();
        }

        protected Socket createSocket() throws IOException {
            SSLSocket sslSocket = (SSLSocket)sslSocketFactory.createSocket(server.getHost(), server.getPortOrDefault(DEFAULT_PORT));
            sslSocket.startHandshake();

            return sslSocket;
        }
    }

    public static class ProxyTcpOverTlsTransport extends TcpOverTlsTransport {
        public static final int DEFAULT_PROXY_PORT = 1080;

        private HostAndPort proxy;

        public ProxyTcpOverTlsTransport(HostAndPort server, HostAndPort proxy) throws KeyManagementException, NoSuchAlgorithmException {
            super(server);
            this.proxy = proxy;
        }

        public ProxyTcpOverTlsTransport(HostAndPort server, File crtFile, HostAndPort proxy) throws IOException, CertificateException, NoSuchAlgorithmException, KeyStoreException, KeyManagementException {
            super(server, crtFile);
            this.proxy = proxy;
        }

        @Override
        protected Socket createSocket() throws IOException {
            InetSocketAddress proxyAddr = new InetSocketAddress(proxy.getHost(), proxy.getPortOrDefault(DEFAULT_PROXY_PORT));
            Socket underlying = new Socket(new Proxy(Proxy.Type.SOCKS, proxyAddr));
            underlying.connect(new InetSocketAddress(server.getHost(), server.getPortOrDefault(DEFAULT_PORT)));
            SSLSocket sslSocket = (SSLSocket)sslSocketFactory.createSocket(underlying, proxy.getHost(), proxy.getPortOrDefault(DEFAULT_PROXY_PORT), true);
            sslSocket.startHandshake();

            return sslSocket;
        }
    }

    public static class ServerVersionService extends Service<List<String>> {
        @Override
        protected Task<List<String>> createTask() {
            return new Task<List<String>>() {
                protected List<String> call() throws ServerException {
                    ElectrumServer electrumServer = new ElectrumServer();
                    return electrumServer.getServerVersion();
                }
            };
        }
    }

    public static class ServerBannerService extends Service<String> {
        @Override
        protected Task<String> createTask() {
            return new Task<>() {
                protected String call() throws ServerException {
                    ElectrumServer electrumServer = new ElectrumServer();
                    return electrumServer.getServerBanner();
                }
            };
        }
    }

    public static class PingService extends ScheduledService<String> {
        private boolean firstCall = true;

        @Override
        protected Task<String> createTask() {
            return new Task<>() {
                protected String call() throws ServerException {
                    ElectrumServer electrumServer = new ElectrumServer();
                    if(firstCall) {
                        electrumServer.getServerVersion();
                        firstCall = false;
                        return electrumServer.getServerBanner();
                    } else {
                        electrumServer.ping();
                    }

                    return null;
                }
            };
        }

        @Override
        public boolean cancel() {
            try {
                closeActiveConnection();
            } catch (ServerException e) {
                e.printStackTrace();
            }

            return super.cancel();
        }

        @Override
        public void reset() {
            super.reset();
            firstCall = true;
        }
    }

    public static class TransactionHistoryService extends Service<Boolean> {
        private final Wallet wallet;

        public TransactionHistoryService(Wallet wallet) {
            this.wallet = wallet;
        }

        @Override
        protected Task<Boolean> createTask() {
            return new Task<>() {
                protected Boolean call() throws ServerException {
                    ElectrumServer electrumServer = new ElectrumServer();
                    Map<WalletNode, Set<BlockTransactionHash>> nodeTransactionMap = electrumServer.getHistory(wallet);
                    electrumServer.getReferencedTransactions(wallet, nodeTransactionMap);
                    electrumServer.calculateNodeHistory(wallet, nodeTransactionMap);
                    return true;
                }
            };
        }
    }

    public enum Protocol {
        TCP {
            @Override
            public Transport getTransport(HostAndPort server) {
                return new TcpTransport(server);
            }

            @Override
            public Transport getTransport(HostAndPort server, File serverCert) {
                return new TcpTransport(server);
            }

            @Override
            public Transport getTransport(HostAndPort server, HostAndPort proxy) {
                throw new UnsupportedOperationException("TCP protocol does not support proxying");
            }

            @Override
            public Transport getTransport(HostAndPort server, File serverCert, HostAndPort proxy) {
                throw new UnsupportedOperationException("TCP protocol does not support proxying");
            }
        },
        SSL{
            @Override
            public Transport getTransport(HostAndPort server) throws KeyManagementException, NoSuchAlgorithmException {
                return new TcpOverTlsTransport(server);
            }

            @Override
            public Transport getTransport(HostAndPort server, File serverCert) throws IOException, CertificateException, NoSuchAlgorithmException, KeyStoreException, KeyManagementException {
                return new TcpOverTlsTransport(server, serverCert);
            }

            @Override
            public Transport getTransport(HostAndPort server, HostAndPort proxy) throws NoSuchAlgorithmException, KeyManagementException {
                return new ProxyTcpOverTlsTransport(server, proxy);
            }

            @Override
            public Transport getTransport(HostAndPort server, File serverCert, HostAndPort proxy) throws IOException, CertificateException, NoSuchAlgorithmException, KeyStoreException, KeyManagementException {
                return new ProxyTcpOverTlsTransport(server, serverCert, proxy);
            }
        };

        public abstract Transport getTransport(HostAndPort server) throws KeyManagementException, NoSuchAlgorithmException;

        public abstract Transport getTransport(HostAndPort server, File serverCert) throws IOException, CertificateException, NoSuchAlgorithmException, KeyStoreException, KeyManagementException;

        public abstract Transport getTransport(HostAndPort server, HostAndPort proxy) throws IOException, CertificateException, NoSuchAlgorithmException, KeyStoreException, KeyManagementException;

        public abstract Transport getTransport(HostAndPort server, File serverCert, HostAndPort proxy) throws IOException, CertificateException, NoSuchAlgorithmException, KeyStoreException, KeyManagementException;

        public HostAndPort getServerHostAndPort(String url) {
            return HostAndPort.fromString(url.substring(this.toUrlString().length()));
        }

        public String toUrlString() {
            return toString().toLowerCase() + "://";
        }

        public String toUrlString(String host) {
            return toUrlString(HostAndPort.fromHost(host));
        }

        public String toUrlString(String host, int port) {
            return toUrlString(HostAndPort.fromParts(host, port));
        }

        public String toUrlString(HostAndPort hostAndPort) {
            return toUrlString() + hostAndPort.toString();
        }

        public static Protocol getProtocol(String url) {
            if(url.startsWith("tcp://")) {
                return TCP;
            }
            if(url.startsWith("ssl://")) {
                return SSL;
            }

            return null;
        }
    }
}
