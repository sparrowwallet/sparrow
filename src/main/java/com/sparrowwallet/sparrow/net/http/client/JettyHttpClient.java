package com.sparrowwallet.sparrow.net.http.client;

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.api.Response;
import org.eclipse.jetty.client.util.FormContentProvider;
import org.eclipse.jetty.client.util.InputStreamResponseListener;
import org.eclipse.jetty.client.util.StringContentProvider;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.util.Fields;
import org.eclipse.jetty.util.component.LifeCycle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class JettyHttpClient extends JacksonHttpClient {
    protected static Logger log = LoggerFactory.getLogger(JettyHttpClient.class);
    public static final String CONTENTTYPE_APPLICATION_JSON = "application/json";

    private final HttpClient httpClient;
    private final long requestTimeout;
    private final HttpUsage httpUsage;

    public JettyHttpClient(Consumer<Exception> onNetworkError, HttpClient httpClient, long requestTimeout, HttpUsage httpUsage) {
        super(onNetworkError);
        this.httpClient = httpClient;
        this.requestTimeout = requestTimeout;
        this.httpUsage = httpUsage;
    }

    @Override
    public void connect() throws HttpException {
        try {
            if(!httpClient.isRunning()) {
                httpClient.start();
            }
        } catch(Exception e) {
            throw new HttpNetworkException(e);
        }
    }

    public void restart() {
        try {
            if(log.isDebugEnabled()) {
                log.debug("restart");
            }
            if(httpClient.isRunning()) {
                httpClient.stop();
            }
            httpClient.start();
        } catch(Exception e) {
            log.error("", e);
        }
    }

    public void stop() {
        try {
            if(httpClient.isRunning()) {
                httpClient.stop();
                Executor executor = httpClient.getExecutor();
                if(executor instanceof LifeCycle) {
                    ((LifeCycle) executor).stop();
                }
            }
        } catch(Exception e) {
            log.error("Error stopping client", e);
        }
    }

    @Override
    protected String requestJsonGet(String urlStr, Map<String, String> headers, boolean async) throws HttpException {
        Request req = computeHttpRequest(urlStr, HttpMethod.GET, headers);
        return makeRequest(req, async);
    }

    @Override
    protected String requestJsonPost(String urlStr, Map<String, String> headers, String jsonBody) throws HttpException {
        Request req = computeHttpRequest(urlStr, HttpMethod.POST, headers);
        req.content(new StringContentProvider(CONTENTTYPE_APPLICATION_JSON, jsonBody, StandardCharsets.UTF_8));
        return makeRequest(req, false);
    }

    @Override
    protected String requestStringPost(String urlStr, Map<String, String> headers, String contentType, String content) throws HttpException {
        log.debug("POST " + urlStr);
        Request req = computeHttpRequest(urlStr, HttpMethod.POST, headers);
        req.content(new StringContentProvider(content), contentType);
        return makeRequest(req, false);
    }

    @Override
    protected String requestJsonPostUrlEncoded(String urlStr, Map<String, String> headers, Map<String, String> body) throws HttpException {
        Request req = computeHttpRequest(urlStr, HttpMethod.POST, headers);
        req.content(new FormContentProvider(computeBodyFields(body)));
        return makeRequest(req, false);
    }

    private Fields computeBodyFields(Map<String, String> body) {
        Fields fields = new Fields();
        for(Map.Entry<String, String> entry : body.entrySet()) {
            fields.put(entry.getKey(), entry.getValue());
        }
        return fields;
    }

    protected String makeRequest(Request req, boolean async) throws HttpException {
        String responseContent;
        if(async) {
            InputStreamResponseListener listener = new InputStreamResponseListener();
            req.send(listener);

            // Call to the listener's get() blocks until the headers arrived
            Response response;
            try {
                response = listener.get(requestTimeout, TimeUnit.MILLISECONDS);
            } catch(Exception e) {
                throw new HttpNetworkException(e);
            }

            // Read content
            InputStream is = listener.getInputStream();
            Scanner s = new Scanner(is).useDelimiter("\\A");
            responseContent = s.hasNext() ? s.next() : null;

            // check status
            checkResponseStatus(response.getStatus(), responseContent);
        } else {
            ContentResponse response;
            try {
                response = req.send();
            } catch(Exception e) {
                throw new HttpNetworkException(e);
            }
            checkResponseStatus(response.getStatus(), response.getContentAsString());
            responseContent = response.getContentAsString();
        }
        return responseContent;
    }

    private void checkResponseStatus(int status, String responseBody) throws HttpResponseException {
        if(!HttpStatus.isSuccess(status)) {
            log.error("Http query failed: status=" + status + ", responseBody=" + responseBody);
            throw new HttpResponseException(responseBody, status);
        }
    }

    public HttpClient getJettyHttpClient() throws HttpException {
        connect();
        return httpClient;
    }

    private Request computeHttpRequest(String url, HttpMethod method, Map<String, String> headers) throws HttpException {
        if(url.endsWith("/rpc")) {
            // log RPC as TRACE
            if(log.isTraceEnabled()) {
                String headersStr = headers != null ? " (" + headers.keySet() + ")" : "";
                log.trace("+" + method + ": " + url + headersStr);
            }
        } else {
            if(log.isDebugEnabled()) {
                String headersStr = headers != null ? " (" + headers.keySet() + ")" : "";
                log.debug("+" + method + ": " + url + headersStr);
            }
        }
        Request req = getJettyHttpClient().newRequest(url);
        req.method(method);
        if(headers != null) {
            for(Map.Entry<String, String> entry : headers.entrySet()) {
                req.header(entry.getKey(), entry.getValue());
            }
        }
        req.timeout(requestTimeout, TimeUnit.MILLISECONDS);
        return req;
    }

    public HttpUsage getHttpUsage() {
        return httpUsage;
    }
}
