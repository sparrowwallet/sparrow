package com.sparrowwallet.sparrow.net;

import com.google.common.net.HostAndPort;
import com.sparrowwallet.tern.http.client.TorHttpProxySupplier;

public class HttpProxySupplier extends TorHttpProxySupplier {
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
