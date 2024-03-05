package com.sparrowwallet.sparrow.net;

import com.google.common.net.HostAndPort;
import com.samourai.http.client.JettyHttpClientService;
import com.samourai.wallet.httpClient.HttpUsage;
import com.samourai.wallet.httpClient.IHttpClient;
import com.samourai.wallet.util.AsyncUtil;
import com.samourai.whirlpool.client.utils.ClientUtils;
import io.reactivex.Observable;
import javafx.concurrent.Service;
import javafx.concurrent.Task;

import java.util.Map;
import java.util.Optional;

public class HttpClientService extends JettyHttpClientService {
    private static final int REQUEST_TIMEOUT = 120000;

    public HttpClientService(HostAndPort torProxy) {
        super(REQUEST_TIMEOUT,
                ClientUtils.USER_AGENT,
                new HttpProxySupplier(torProxy));
    }

    public <T> T requestJson(String url, Class<T> responseType, Map<String, String> headers) throws Exception {
        return getHttpClient(HttpUsage.BACKEND)
                .getJson(url, responseType, headers);
    }

    public <T> Observable<Optional<T>> postJson(String url, Class<T> responseType, Map<String, String> headers, Object body) {
        return getHttpClient(HttpUsage.BACKEND)
                .postJson(url, responseType, headers, body).toObservable();
    }

    public String postString(String url, Map<String, String> headers, String contentType, String content) throws Exception {
        IHttpClient httpClient = getHttpClient(HttpUsage.BACKEND);
        return AsyncUtil.getInstance().blockingGet(
                httpClient.postString(url, headers, contentType, content)).get();
    }

    public HostAndPort getTorProxy() {
        return getHttpProxySupplier().getTorProxy();
    }

    public void setTorProxy(HostAndPort torProxy) {
        //Ensure all http clients are shutdown first
        stop();
        getHttpProxySupplier()._setTorProxy(torProxy);
    }

    @Override
    public HttpProxySupplier getHttpProxySupplier() {
        return (HttpProxySupplier)super.getHttpProxySupplier();
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
