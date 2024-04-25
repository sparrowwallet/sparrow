package com.sparrowwallet.sparrow.net;

import com.google.common.net.HostAndPort;
import com.sparrowwallet.sparrow.net.http.client.HttpProxy;
import com.sparrowwallet.sparrow.net.http.client.HttpProxyProtocol;
import com.sparrowwallet.sparrow.net.http.client.HttpUsage;
import com.sparrowwallet.sparrow.net.http.client.IHttpProxySupplier;

import java.util.Optional;

public class HttpProxySupplier implements IHttpProxySupplier {
    private HostAndPort torProxy;
    private HttpProxy httpProxy;

    public HttpProxySupplier(HostAndPort torProxy) {
        this.torProxy = torProxy;
        this.httpProxy = computeHttpProxy(torProxy);
    }

    private HttpProxy computeHttpProxy(HostAndPort hostAndPort) {
        if (hostAndPort == null) {
            return null;
        }

        return new HttpProxy(HttpProxyProtocol.SOCKS, hostAndPort.getHost(), hostAndPort.getPort());
    }

    public HostAndPort getTorProxy() {
        return torProxy;
    }

    // shouldnt call directly but use httpClientService.setTorProxy()
    public void _setTorProxy(HostAndPort hostAndPort) {
        // set proxy
        this.torProxy = hostAndPort;
        this.httpProxy = computeHttpProxy(hostAndPort);
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
