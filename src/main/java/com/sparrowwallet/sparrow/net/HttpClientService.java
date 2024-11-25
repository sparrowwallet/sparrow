package com.sparrowwallet.sparrow.net;

import com.google.common.net.HostAndPort;
import javafx.concurrent.Service;
import javafx.concurrent.Task;

public class HttpClientService extends com.sparrowwallet.tern.http.client.HttpClientService {
    public HttpClientService(HostAndPort torProxy) {
        super(new HttpProxySupplier(torProxy));
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
