package com.sparrowwallet.sparrow.net.http.client;

import com.google.common.util.concurrent.RateLimiter;
import com.sparrowwallet.sparrow.net.http.client.socks5.Socks5Proxy;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.ProxyConfiguration;
import org.eclipse.jetty.client.Socks4Proxy;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.eclipse.jetty.util.thread.ScheduledExecutorScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

@SuppressWarnings("deprecation")
public class JettyHttpClientService implements IHttpClientService {
    private static final Logger log = LoggerFactory.getLogger(JettyHttpClientService.class);
    private static final String NAME = "HttpClient";
    public static final long DEFAULT_TIMEOUT = 30000;

    // limit changing Tor identity on network error every 4 minutes
    private static final double RATE_CHANGE_IDENTITY_ON_NETWORK_ERROR = 1.0 / 240;

    protected Map<HttpUsage, JettyHttpClient> httpClients; // used by Sparrow
    private final IHttpProxySupplier httpProxySupplier;
    private final long requestTimeout;

    public JettyHttpClientService(long requestTimeout, IHttpProxySupplier httpProxySupplier) {
        this.httpProxySupplier = httpProxySupplier != null ? httpProxySupplier : computeHttpProxySupplierDefault();
        this.requestTimeout = requestTimeout;
        this.httpClients = new ConcurrentHashMap<>();
    }

    public JettyHttpClientService(long requestTimeout) {
        this(requestTimeout, null);
    }

    public JettyHttpClientService() {
        this(DEFAULT_TIMEOUT);
    }

    protected static IHttpProxySupplier computeHttpProxySupplierDefault() {
        return new IHttpProxySupplier() {
            @Override
            public Optional<HttpProxy> getHttpProxy(HttpUsage httpUsage) {
                return Optional.empty();
            }

            @Override
            public void changeIdentity() {
            }
        };
    }

    @Override
    public JettyHttpClient getHttpClient(HttpUsage httpUsage) {
        JettyHttpClient httpClient = httpClients.get(httpUsage);
        if(httpClient == null) {
            if(log.isDebugEnabled()) {
                log.debug("+httpClient[" + httpUsage + "]");
            }
            httpClient = computeHttpClient(httpUsage);
            httpClients.put(httpUsage, httpClient);
        }
        return httpClient;
    }

    protected JettyHttpClient computeHttpClient(HttpUsage httpUsage) {
        Consumer<Exception> onNetworkError = computeOnNetworkError();
        HttpClient httpClient = computeJettyClient(httpUsage);
        return new JettyHttpClient(onNetworkError, httpClient, requestTimeout, httpUsage);
    }

    protected HttpClient computeJettyClient(HttpUsage httpUsage) {
        // we use jetty for proxy SOCKS support
        HttpClient jettyHttpClient = new HttpClient(new SslContextFactory());
        // jettyHttpClient.setSocketAddressResolver(new MySocketAddressResolver());

        // prevent user-agent tracking
        jettyHttpClient.setUserAgentField(null);

        // configure
        configureProxy(jettyHttpClient, httpUsage);
        configureThread(jettyHttpClient, httpUsage);

        return jettyHttpClient;
    }

    protected Consumer<Exception> computeOnNetworkError() {
        RateLimiter rateLimiter = RateLimiter.create(RATE_CHANGE_IDENTITY_ON_NETWORK_ERROR);
        return e -> {
            if(!rateLimiter.tryAcquire()) {
                if(log.isDebugEnabled()) {
                    log.debug("onNetworkError: not changing Tor identity (too many recent attempts)");
                }
                return;
            }
            // change Tor identity on network error
            httpProxySupplier.changeIdentity();
        };
    }

    protected void configureProxy(HttpClient jettyHttpClient, HttpUsage httpUsage) {
        Optional<HttpProxy> httpProxyOptional = httpProxySupplier.getHttpProxy(httpUsage);
        if(httpProxyOptional != null && httpProxyOptional.isPresent()) {
            HttpProxy httpProxy = httpProxyOptional.get();
            if(log.isDebugEnabled()) {
                log.debug("+httpClient: proxy=" + httpProxy);
            }
            ProxyConfiguration.Proxy jettyProxy = computeJettyProxy(httpProxy);
            jettyHttpClient.getProxyConfiguration().getProxies().add(jettyProxy);
        } else {
            if(log.isDebugEnabled()) {
                log.debug("+httpClient: no proxy");
            }
        }
    }

    protected void configureThread(HttpClient jettyHttpClient, HttpUsage httpUsage) {
        String name = NAME + "-" + httpUsage.toString();

        QueuedThreadPool threadPool = new QueuedThreadPool();
        threadPool.setName(name);
        threadPool.setDaemon(true);
        jettyHttpClient.setExecutor(threadPool);
        jettyHttpClient.setScheduler(new ScheduledExecutorScheduler(name + "-scheduler", true));
    }

    protected ProxyConfiguration.Proxy computeJettyProxy(HttpProxy httpProxy) {
        ProxyConfiguration.Proxy jettyProxy = null;
        switch(httpProxy.getProtocol()) {
            case SOCKS:
                jettyProxy = new Socks4Proxy(httpProxy.getHost(), httpProxy.getPort());
                break;
            case SOCKS5:
                jettyProxy = new Socks5Proxy(httpProxy.getHost(), httpProxy.getPort());
                break;
            case HTTP:
                jettyProxy = new org.eclipse.jetty.client.HttpProxy(httpProxy.getHost(), httpProxy.getPort());
                break;
        }
        return jettyProxy;
    }

    @Override
    public synchronized void stop() {
        for(JettyHttpClient httpClient : httpClients.values()) {
            httpClient.stop();
        }
        httpClients.clear();
    }

    @Override
    public void changeIdentity() {
        stop();
        httpProxySupplier.changeIdentity();
    }

    public IHttpProxySupplier getHttpProxySupplier() {
        return httpProxySupplier;
    }
}
