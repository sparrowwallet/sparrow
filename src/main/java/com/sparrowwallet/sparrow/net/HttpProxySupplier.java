package com.sparrowwallet.sparrow.net;

import com.google.common.net.HostAndPort;

public class HttpProxySupplier extends com.sparrowwallet.tern.http.client.HttpProxySupplier {
    public HttpProxySupplier(HostAndPort torProxy) {
        super(torProxy);
    }

    @Override
    public void changeIdentity() {
        HostAndPort torProxy = getTorProxy();
        if(torProxy != null) {
            TorUtils.changeIdentity(torProxy);
        }
    }
}
