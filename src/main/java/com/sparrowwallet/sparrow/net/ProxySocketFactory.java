package com.sparrowwallet.sparrow.net;

import com.google.common.net.HostAndPort;

import javax.net.SocketFactory;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Socket;

import static com.sparrowwallet.sparrow.net.ProxyTcpOverTlsTransport.DEFAULT_PROXY_PORT;

public class ProxySocketFactory extends SocketFactory {
    private final Proxy proxy;

    public ProxySocketFactory() {
        this(Proxy.NO_PROXY);
    }

    public ProxySocketFactory(HostAndPort proxyHostAndPort) {
        this(getSocksProxy(proxyHostAndPort.getHost(), proxyHostAndPort.getPortOrDefault(DEFAULT_PROXY_PORT)));
    }

    public ProxySocketFactory(Proxy proxy) {
        this.proxy = proxy;
    }

    @Override
    public Socket createSocket() {
        return new Socket(proxy);
    }

    @Override
    public Socket createSocket(String address, int port) throws IOException {
        return createSocket(createAddress(address, port), null);
    }

    @Override
    public Socket createSocket(String address, int port, InetAddress localAddress, int localPort) throws IOException {
        return createSocket(createAddress(address, port), new InetSocketAddress(localAddress, localPort));
    }

    @Override
    public Socket createSocket(InetAddress address, int port) throws IOException {
        return createSocket(new InetSocketAddress(address, port), null);
    }

    @Override
    public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort) throws IOException {
        return createSocket(new InetSocketAddress(address, port), new InetSocketAddress(localAddress, localPort));
    }

    private InetSocketAddress createAddress(String address, int port) {
        if(Protocol.isOnionHost(address)) {
            return InetSocketAddress.createUnresolved(address, port);
        } else {
            return new InetSocketAddress(address, port);
        }
    }

    private Socket createSocket(InetSocketAddress address, InetSocketAddress bindAddress) throws IOException {
        Socket socket = new Socket(proxy);
        if(bindAddress != null) {
            socket.bind(bindAddress);
        }

        socket.connect(address);
        return socket;
    }

    private static Proxy getSocksProxy(String proxyAddress, int proxyPort) {
        return new Proxy(Proxy.Type.SOCKS, new InetSocketAddress(proxyAddress, proxyPort));
    }
}
