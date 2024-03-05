package com.sparrowwallet.sparrow.whirlpool.dataSource;

import com.samourai.wallet.api.backend.seenBackend.ISeenBackend;
import com.samourai.wallet.api.backend.seenBackend.SeenResponse;
import com.samourai.wallet.httpClient.IHttpClient;

import java.util.Collection;

public class SparrowSeenBackend implements ISeenBackend {
    private IHttpClient httpClient;

    public SparrowSeenBackend(IHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public SeenResponse seen(Collection<String> addresses) throws Exception {
        return null; // TODO implement: check if each address already received funds
    }

    @Override
    public boolean seen(String address) throws Exception {
        return false; // TODO implement: return true if address already received funds
    }

    @Override
    public IHttpClient getHttpClient() {
        return httpClient;
    }
}
