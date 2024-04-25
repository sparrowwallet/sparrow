package com.sparrowwallet.sparrow.net.http.client;

public interface IHttpClientService {
    IHttpClient getHttpClient(HttpUsage httpUsage);

    void changeIdentity(); // change Tor identity if any

    void stop();
}
