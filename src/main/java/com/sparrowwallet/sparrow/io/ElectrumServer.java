package com.sparrowwallet.sparrow.io;

import com.github.arteam.simplejsonrpc.client.*;
import com.github.arteam.simplejsonrpc.client.builder.BatchRequestBuilder;
import com.google.common.net.HostAndPort;
import com.sparrowwallet.drongo.KeyPurpose;
import com.sparrowwallet.drongo.Utils;
import com.sparrowwallet.drongo.protocol.Sha256Hash;
import com.sparrowwallet.drongo.protocol.Transaction;
import com.sparrowwallet.drongo.wallet.BlockchainTransactionHash;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.drongo.wallet.WalletNode;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import org.jetbrains.annotations.NotNull;

import javax.net.SocketFactory;
import javax.net.ssl.*;
import java.io.*;
import java.net.Socket;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.stream.Collectors;

public class ElectrumServer {
    private static Transport transport;

    private synchronized Transport getTransport() throws ServerException {
        if(transport == null) {
            try {
                String electrumServer = Config.get().getElectrumServer();
                File electrumServerCert = Config.get().getElectrumServerCert();

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
                transport = protocol.getTransport(server, electrumServerCert);
            } catch (Exception e) {
                throw new ServerException(e);
            }
        }

        return transport;
    }

    public String getServerVersion() throws ServerException {
        JsonRpcClient client = new JsonRpcClient(getTransport());
        List<String> serverVersion = client.createRequest().returnAsList(String.class).method("server.version").id(1).param("client_name", "Sparrow").param("protocol_version", "1.4").execute();
        return serverVersion.get(1);
    }

    public void getHistory(Wallet wallet) throws ServerException {
        getHistory(wallet, KeyPurpose.RECEIVE);
        getHistory(wallet, KeyPurpose.CHANGE);
    }

    public void getHistory(Wallet wallet, KeyPurpose keyPurpose) throws ServerException {
        getHistory(wallet, wallet.getNode(keyPurpose).getChildren());
        getMempool(wallet, wallet.getNode(keyPurpose).getChildren());
    }

    public void getHistory(Wallet wallet, Collection<WalletNode> nodes) throws ServerException {
        getReferences(wallet, "blockchain.scripthash.get_history", nodes);
    }

    public void getMempool(Wallet wallet, Collection<WalletNode> nodes) throws ServerException {
        getReferences(wallet, "blockchain.scripthash.get_mempool", nodes);
    }

    public void getReferences(Wallet wallet, String method, Collection<WalletNode> nodes) throws ServerException {
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
                    Set<BlockchainTransactionHash> references = Arrays.stream(txes).map(ScriptHashTx::getBlockchainTransactionHash).collect(Collectors.toSet());

                    for(BlockchainTransactionHash reference : references) {
                        if(!node.getHistory().add(reference)) {
                            Optional<BlockchainTransactionHash> optionalReference = node.getHistory().stream().filter(tr -> tr.getHash().equals(reference.getHash())).findFirst();
                            if(optionalReference.isPresent()) {
                                BlockchainTransactionHash existingReference = optionalReference.get();
                                if(existingReference.getHeight() < reference.getHeight()) {
                                    node.getHistory().remove(existingReference);
                                    node.getHistory().add(reference);
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

    public void getReferencedTransactions(Wallet wallet) throws ServerException {
        getReferencedTransactions(wallet, KeyPurpose.RECEIVE);
        getReferencedTransactions(wallet, KeyPurpose.CHANGE);
    }

    public void getReferencedTransactions(Wallet wallet, KeyPurpose keyPurpose) throws ServerException {
        WalletNode purposeNode = wallet.getNode(keyPurpose);
        Set<BlockchainTransactionHash> references = new HashSet<>();
        for(WalletNode addressNode : purposeNode.getChildren()) {
            references.addAll(addressNode.getHistory());
        }

        Map<Sha256Hash, Transaction> transactionMap = getTransactions(references);
        wallet.getTransactions().putAll(transactionMap);
    }

    public Map<Sha256Hash, Transaction> getTransactions(Set<BlockchainTransactionHash> references) throws ServerException {
        try {
            JsonRpcClient client = new JsonRpcClient(getTransport());
            BatchRequestBuilder<String, String> batchRequest = client.createBatchRequest().keysType(String.class).returnType(String.class);
            for(BlockchainTransactionHash reference : references) {
                batchRequest.add(reference.getHashAsString(), "blockchain.transaction.get", reference.getHashAsString());
            }
            Map<String, String> result = batchRequest.execute();

            Map<Sha256Hash, Transaction> transactionMap = new HashMap<>();
            for(String txid : result.keySet()) {
                Sha256Hash hash = Sha256Hash.wrap(txid);
                byte[] rawtx = Utils.hexToBytes(result.get(txid));
                Transaction transaction = new Transaction(rawtx);
                transactionMap.put(hash, transaction);
            }

            return transactionMap;
        } catch (IllegalStateException e) {
            throw new ServerException(e.getCause());
        } catch (Exception e) {
            throw new ServerException(e);
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

        public BlockchainTransactionHash getBlockchainTransactionHash() {
            Sha256Hash hash = Sha256Hash.wrap(tx_hash);
            return new BlockchainTransactionHash(hash, height, fee);
        }

        @Override
        public String toString() {
            return "ScriptHashTx{" +
                    "height=" + height +
                    ", tx_hash='" + tx_hash + '\'' +
                    ", fee=" + fee +
                    '}';
        }
    }

    private static class TcpTransport implements Transport {
        private static final int DEFAULT_PORT = 50001;

        protected final HostAndPort server;
        private final SocketFactory socketFactory;

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
    }

    private static class TcpOverTlsTransport extends TcpTransport {
        private static final int DEFAULT_PORT = 50002;

        private final SSLSocketFactory sslSocketFactory;

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
                    electrumServer.getHistory(wallet);
                    electrumServer.getReferencedTransactions(wallet);
                    return true;
                }
            };
        }
    }

    public enum Protocol {
        TCP {
            @Override
            public Transport getTransport(HostAndPort server, File serverCert) throws IOException {
                return new TcpTransport(server);
            }
        },
        SSL{
            @Override
            public Transport getTransport(HostAndPort server, File serverCert) throws IOException, CertificateException, NoSuchAlgorithmException, KeyStoreException, KeyManagementException {
                if(serverCert != null && serverCert.exists()) {
                    return new TcpOverTlsTransport(server, serverCert);
                } else {
                    return new TcpOverTlsTransport(server);
                }
            }
        };

        public abstract Transport getTransport(HostAndPort server, File serverCert) throws IOException, CertificateException, NoSuchAlgorithmException, KeyStoreException, KeyManagementException;

        public HostAndPort getServerHostAndPort(String url) {
            return HostAndPort.fromString(url.substring(this.toUrlString().length()));
        }

        public String toUrlString() {
            return toString().toLowerCase() + "://";
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
