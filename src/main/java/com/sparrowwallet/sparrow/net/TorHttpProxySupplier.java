package com.sparrowwallet.sparrow.net;

import com.google.common.net.HostAndPort;
import com.sparrowwallet.tern.http.client.*;

import java.util.Optional;

public class TorHttpProxySupplier implements IHttpProxySupplier {
    private HostAndPort torProxy;
    private HttpProxy httpProxy;

    public TorHttpProxySupplier(HostAndPort torProxy) {
        this.torProxy = torProxy;
        this.httpProxy = computeHttpProxy(torProxy);
    }

    private HttpProxy computeHttpProxy(HostAndPort hostAndPort) {
        if (hostAndPort == null) {
            return null;
        }

        return new HttpProxy(HttpProxyProtocol.SOCKS, hostAndPort.getHost(), hostAndPort.getPortOrDefault(ProxyTcpOverTlsTransport.DEFAULT_PROXY_PORT));
    }

    public HostAndPort getTorProxy() {
        return torProxy;
    }

    // shouldn't call directly but use httpClientService.setTorProxy()
    public void _setTorProxy(HostAndPort hostAndPort) {
        //The proxy to apply is computed first, so a proxy that cannot be applied is not reported as the one in use
        this.httpProxy = computeHttpProxy(hostAndPort);
        this.torProxy = hostAndPort;
    }

    @Override
    public Optional<HttpProxy> getHttpProxy(HttpUsage httpUsage) {
        return Optional.ofNullable(httpProxy);
    }

    @Override
    public void changeIdentity() {
        HostAndPort torProxy = getTorProxy();
        if(torProxy != null) {
            TorUtils.changeIdentity(torProxy);
        }
    }
}
