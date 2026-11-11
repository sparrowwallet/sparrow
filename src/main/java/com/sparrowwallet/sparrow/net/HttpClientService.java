package com.sparrowwallet.sparrow.net;

import com.google.common.net.HostAndPort;
import javafx.concurrent.Service;
import javafx.concurrent.Task;

public class HttpClientService extends com.sparrowwallet.tern.http.client.HttpClientService {
    public HttpClientService(HostAndPort torProxy) {
        super(new TorHttpProxySupplier(torProxy));
    }

    public HostAndPort getTorProxy() {
        if(getHttpProxySupplier() instanceof TorHttpProxySupplier torHttpProxySupplier) {
            return torHttpProxySupplier.getTorProxy();
        }

        return null;
    }

    public void setTorProxy(HostAndPort torProxy) {
        if(getHttpProxySupplier() instanceof TorHttpProxySupplier torHttpProxySupplier) {
            //A client takes its proxy when created, so the proxy is changed before the clients created with the previous one are stopped. Holding the lock that
            //getHttpClient() takes ensures no client is created in between.
            synchronized(this) {
                torHttpProxySupplier._setTorProxy(torProxy);
                stop();
            }
        }
    }

    public static class ShutdownService extends Service<Boolean> {
        private final HttpClientService httpClientService;

        public ShutdownService(HttpClientService httpClientService) {
            this.httpClientService = httpClientService;
        }

        @Override
        protected Task<Boolean> createTask() {
            return new Task<>() {
                protected Boolean call() throws Exception {
                    httpClientService.stop();
                    return true;
                }
            };
        }
    }
}
