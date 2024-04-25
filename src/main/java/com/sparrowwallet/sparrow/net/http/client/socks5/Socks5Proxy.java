//
// ========================================================================
// Copyright (c) 1995 Mort Bay Consulting Pty Ltd and others.
//
// This program and the accompanying materials are made available under the
// terms of the Eclipse Public License v. 2.0 which is available at
// https://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
// which is available at https://www.apache.org/licenses/LICENSE-2.0.
//
// SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
// ========================================================================
//

package com.sparrowwallet.sparrow.net.http.client.socks5;

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.HttpClientTransport;
import org.eclipse.jetty.client.HttpDestination;
import org.eclipse.jetty.client.Origin;
import org.eclipse.jetty.client.ProxyConfiguration.Proxy;
import org.eclipse.jetty.io.AbstractConnection;
import org.eclipse.jetty.io.ClientConnectionFactory;
import org.eclipse.jetty.io.Connection;
import org.eclipse.jetty.io.EndPoint;
import org.eclipse.jetty.io.ssl.SslClientConnectionFactory;
import org.eclipse.jetty.util.BufferUtil;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.util.Promise;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Client-side proxy configuration for SOCKS5, defined by <a
 * href="https://datatracker.ietf.org/doc/html/rfc1928">RFC 1928</a>.
 *
 * <p>Multiple authentication methods are supported via {@link
 * #putAuthenticationFactory(Socks5.Authentication.Factory)}. By default only the {@link
 * Socks5.NoAuthenticationFactory NO AUTH} authentication method is configured. The {@link
 * Socks5.UsernamePasswordAuthenticationFactory USERNAME/PASSWORD} is available to applications but
 * must be explicitly configured and added.
 */
public class Socks5Proxy extends Proxy {
    private static final Logger LOG = LoggerFactory.getLogger(Socks5Proxy.class);

    private final Map<Byte, Socks5.Authentication.Factory> authentications = new LinkedHashMap<>();

    /**
     * Creates a new instance with the given SOCKS5 proxy host and port.
     *
     * @param host the SOCKS5 proxy host name
     * @param port the SOCKS5 proxy port
     */
    public Socks5Proxy(String host, int port) {
        this(new Origin.Address(host, port), false);
    }

    /**
     * Creates a new instance with the given SOCKS5 proxy address.
     *
     * <p>When {@code secure=true} the communication between the client and the proxy will be
     * encrypted (using this proxy {@link #getSslContextFactory()} which typically defaults to that of
     * {@link HttpClient}.
     *
     * @param address the SOCKS5 proxy address (host and port)
     * @param secure  whether the communication between the client and the SOCKS5 proxy should be
     *                secure
     */
    public Socks5Proxy(Origin.Address address, boolean secure) {
        super(address, secure);
        putAuthenticationFactory(new Socks5.NoAuthenticationFactory());
    }

    protected static ClientConnectionFactory newSslClientConnectionFactory(HttpClient httpClient, SslContextFactory sslContextFactory, ClientConnectionFactory connectionFactory) {
        if(sslContextFactory == null) {
            sslContextFactory = httpClient.getSslContextFactory();
        }
        return new SslClientConnectionFactory(sslContextFactory, httpClient.getByteBufferPool(), httpClient.getExecutor(), connectionFactory);
    }

    /**
     * Provides this class with the given SOCKS5 authentication method.
     *
     * @param authenticationFactory the SOCKS5 authentication factory
     * @return the previous authentication method of the same type, or {@code null} if there was none
     * of that type already present
     */
    public Socks5.Authentication.Factory putAuthenticationFactory(Socks5.Authentication.Factory authenticationFactory) {
        return authentications.put(authenticationFactory.getMethod(), authenticationFactory);
    }

    /**
     * Removes the authentication of the given {@code method}.
     *
     * @param method the authentication method to remove
     */
    public Socks5.Authentication.Factory removeAuthenticationFactory(byte method) {
        return authentications.remove(method);
    }

    @Override
    public ClientConnectionFactory newClientConnectionFactory(ClientConnectionFactory connectionFactory) {
        return new Socks5ProxyClientConnectionFactory(connectionFactory);
    }

    private static class Socks5ProxyConnection extends AbstractConnection implements Connection.UpgradeFrom {
        private static final Pattern IPv4_PATTERN = Pattern.compile("(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})");

        // SOCKS5 response max length is 262 bytes.
        private final ByteBuffer byteBuffer = BufferUtil.allocate(512);
        private final ClientConnectionFactory connectionFactory;
        private final Map<String, Object> context;
        private final Map<Byte, Socks5.Authentication.Factory> authentications;
        private State state = State.HANDSHAKE;

        private Socks5ProxyConnection(EndPoint endPoint, Executor executor, ClientConnectionFactory connectionFactory, Map<String, Object> context, Map<Byte, Socks5.Authentication.Factory> authentications) {
            super(endPoint, executor);
            this.connectionFactory = connectionFactory;
            this.context = context;
            this.authentications = new LinkedHashMap<>(authentications);
        }

        @Override
        public ByteBuffer onUpgradeFrom() {
            return BufferUtil.copy(byteBuffer);
        }

        @Override
        public void onOpen() {
            super.onOpen();
            sendHandshake();
        }

        private void sendHandshake() {
            try {
                // +-------------+--------------------+------------------+
                // | version (1) | num of methods (1) | methods (1..255) |
                // +-------------+--------------------+------------------+
                int size = authentications.size();
                ByteBuffer byteBuffer =
                        ByteBuffer.allocate(1 + 1 + size).put(Socks5.VERSION).put((byte) size);
                authentications.keySet().forEach(byteBuffer::put);
                byteBuffer.flip();
                getEndPoint().write(Callback.from(this::handshakeSent, this::fail), byteBuffer);
            } catch(Throwable x) {
                fail(x);
            }
        }

        private void handshakeSent() {
            if(LOG.isDebugEnabled()) {
                LOG.debug("Written SOCKS5 handshake request");
            }
            state = State.HANDSHAKE;
            fillInterested();
        }

        private void fail(Throwable x) {
            if(LOG.isDebugEnabled()) {
                LOG.debug("SOCKS5 failure", x);
            }
            getEndPoint().close();
            @SuppressWarnings("unchecked")
            Promise<Connection> promise =
                    (Promise<Connection>)
                            this.context.get(HttpClientTransport.HTTP_CONNECTION_PROMISE_CONTEXT_KEY);
            promise.failed(x);
        }

        @Override
        public void onFillable() {
            try {
                switch(state) {
                    case HANDSHAKE:
                        receiveHandshake();
                        break;
                    case CONNECT:
                        receiveConnect();
                        break;
                    default:
                        throw new IllegalStateException();
                }
            } catch(Throwable x) {
                fail(x);
            }
        }

        private void receiveHandshake() throws IOException {
            // +-------------+------------+
            // | version (1) | method (1) |
            // +-------------+------------+
            int filled = getEndPoint().fill(byteBuffer);
            if(filled < 0) {
                throw new ClosedChannelException();
            }
            if(byteBuffer.remaining() < 2) {
                fillInterested();
                return;
            }

            if(LOG.isDebugEnabled()) {
                LOG.debug("Received SOCKS5 handshake response {}", BufferUtil.toDetailString(byteBuffer));
            }

            byte version = byteBuffer.get();
            if(version != Socks5.VERSION) {
                throw new IOException("Unsupported SOCKS5 version: " + version);
            }

            byte method = byteBuffer.get();
            if(method == -1) {
                throw new IOException("Unacceptable SOCKS5 authentication methods");
            }

            Socks5.Authentication.Factory factory = authentications.get(method);
            if(factory == null) {
                throw new IOException("Unknown SOCKS5 authentication method: " + method);
            }

            factory
                    .newAuthentication()
                    .authenticate(getEndPoint(), Callback.from(this::sendConnect, this::fail));
        }

        private void sendConnect() {
            try {
                // +-------------+-------------+--------------+------------------+------------------------+----------+
                // | version (1) | command (1) | reserved (1) | address type (1) | address bytes (4..255) |
                // port (2) |
                // +-------------+-------------+--------------+------------------+------------------------+----------+
                HttpDestination destination = (HttpDestination) context.get(HttpClientTransport.HTTP_DESTINATION_CONTEXT_KEY);
                Origin.Address address = destination.getOrigin().getAddress();
                String host = address.getHost();
                short port = (short) address.getPort();

                ByteBuffer byteBuffer;
                Matcher matcher = IPv4_PATTERN.matcher(host);
                if(matcher.matches()) {
                    byteBuffer =
                            ByteBuffer.allocate(10)
                                    .put(Socks5.VERSION)
                                    .put(Socks5.COMMAND_CONNECT)
                                    .put(Socks5.RESERVED)
                                    .put(Socks5.ADDRESS_TYPE_IPV4);
                    for(int i = 1; i <= 4; ++i) {
                        byteBuffer.put(Byte.parseByte(matcher.group(i)));
                    }
                    byteBuffer.putShort(port).flip();
                } else if(true /*URIUtil.isValidHostRegisteredName(host)*/) {
                    byte[] bytes = host.getBytes(StandardCharsets.US_ASCII);
                    if(bytes.length > 255) {
                        throw new IOException("Invalid host name: " + host);
                    }
                    byteBuffer =
                            (ByteBuffer)
                                    ByteBuffer.allocate(7 + bytes.length)
                                            .put(Socks5.VERSION)
                                            .put(Socks5.COMMAND_CONNECT)
                                            .put(Socks5.RESERVED)
                                            .put(Socks5.ADDRESS_TYPE_DOMAIN)
                                            .put((byte) bytes.length)
                                            .put(bytes)
                                            .putShort(port)
                                            .flip();
                } else {
                    // Assume IPv6.
                    byte[] bytes = InetAddress.getByName(host).getAddress();
                    byteBuffer =
                            (ByteBuffer)
                                    ByteBuffer.allocate(22)
                                            .put(Socks5.VERSION)
                                            .put(Socks5.COMMAND_CONNECT)
                                            .put(Socks5.RESERVED)
                                            .put(Socks5.ADDRESS_TYPE_IPV6)
                                            .put(bytes)
                                            .putShort(port)
                                            .flip();
                }

                getEndPoint().write(Callback.from(this::connectSent, this::fail), byteBuffer);
            } catch(Throwable x) {
                fail(x);
            }
        }

        private void connectSent() {
            if(LOG.isDebugEnabled()) {
                LOG.debug("Written SOCKS5 connect request");
            }
            state = State.CONNECT;
            fillInterested();
        }

        private void receiveConnect() throws IOException {
            // +-------------+-----------+--------------+------------------+------------------------+----------+
            // | version (1) | reply (1) | reserved (1) | address type (1) | address bytes (4..255) | port
            // (2) |
            // +-------------+-----------+--------------+------------------+------------------------+----------+
            int filled = getEndPoint().fill(byteBuffer);
            if(filled < 0) {
                throw new ClosedChannelException();
            }
            if(byteBuffer.remaining() < 5) {
                fillInterested();
                return;
            }
            byte addressType = byteBuffer.get(3);
            int length = 6;
            if(addressType == Socks5.ADDRESS_TYPE_IPV4) {
                length += 4;
            } else if(addressType == Socks5.ADDRESS_TYPE_DOMAIN) {
                length += 1 + (byteBuffer.get(4) & 0xFF);
            } else if(addressType == Socks5.ADDRESS_TYPE_IPV6) {
                length += 16;
            } else {
                throw new IOException("Invalid SOCKS5 address type: " + addressType);
            }
            if(byteBuffer.remaining() < length) {
                fillInterested();
                return;
            }

            if(LOG.isDebugEnabled()) {
                LOG.debug("Received SOCKS5 connect response {}", BufferUtil.toDetailString(byteBuffer));
            }

            // We have all the SOCKS5 bytes.
            byte version = byteBuffer.get();
            if(version != Socks5.VERSION) {
                throw new IOException("Unsupported SOCKS5 version: " + version);
            }

            byte status = byteBuffer.get();
            switch(status) {
                case 0: {
                    // Consume the buffer before upgrading to the tunnel.
                    byteBuffer.position(length);
                    tunnel();
                    break;
                }
                case 1:
                    throw new IOException("SOCKS5 general failure");
                case 2:
                    throw new IOException("SOCKS5 connection not allowed");
                case 3:
                    throw new IOException("SOCKS5 network unreachable");
                case 4:
                    throw new IOException("SOCKS5 host unreachable");
                case 5:
                    throw new IOException("SOCKS5 connection refused");
                case 6:
                    throw new IOException("SOCKS5 timeout expired");
                case 7:
                    throw new IOException("SOCKS5 unsupported command");
                case 8:
                    throw new IOException("SOCKS5 unsupported address");
                default:
                    throw new IOException("SOCKS5 unknown status: " + status);
            }
        }

        private void tunnel() {
            try {
                HttpDestination destination =
                        (HttpDestination) context.get(HttpClientTransport.HTTP_DESTINATION_CONTEXT_KEY);
                this.context.put("ssl.peer.host", destination.getHost());
                this.context.put("ssl.peer.port", destination.getPort());
                // Origin.Address address = destination.getOrigin().getAddress();
                // Don't want to do DNS resolution here.
                // InetSocketAddress inet = InetSocketAddress.createUnresolved(address.getHost(),
                // address.getPort());
                // context.put(ClientConnector.REMOTE_SOCKET_ADDRESS_CONTEXT_KEY, inet);
                ClientConnectionFactory connectionFactory = this.connectionFactory;
                if(destination.isSecure()) {
                    connectionFactory =
                            newSslClientConnectionFactory(destination.getHttpClient(), null, connectionFactory);
                }
                Connection newConnection = connectionFactory.newConnection(getEndPoint(), context);
                getEndPoint().upgrade(newConnection);
                if(LOG.isDebugEnabled()) {
                    LOG.debug("SOCKS5 tunnel established: {} over {}", this, newConnection);
                }
            } catch(Throwable x) {
                fail(x);
            }
        }

        private enum State {
            HANDSHAKE,
            CONNECT
        }
    }

    private class Socks5ProxyClientConnectionFactory implements ClientConnectionFactory {
        private final ClientConnectionFactory connectionFactory;

        private Socks5ProxyClientConnectionFactory(ClientConnectionFactory connectionFactory) {
            this.connectionFactory = connectionFactory;
        }

        public Connection newConnection(EndPoint endPoint, Map<String, Object> context) {
            HttpDestination destination = (HttpDestination) context.get(HttpClientTransport.HTTP_DESTINATION_CONTEXT_KEY);
            Executor executor = destination.getHttpClient().getExecutor();
            Socks5ProxyConnection connection = new Socks5ProxyConnection(endPoint, executor, connectionFactory, context, authentications);
            return customize(connection, context);
        }
    }
}
