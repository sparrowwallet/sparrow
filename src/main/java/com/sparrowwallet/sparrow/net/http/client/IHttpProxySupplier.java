package com.sparrowwallet.sparrow.net.http.client;

import java.util.Optional;

public interface IHttpProxySupplier {
    Optional<HttpProxy> getHttpProxy(HttpUsage httpUsage);

    void changeIdentity();
}
