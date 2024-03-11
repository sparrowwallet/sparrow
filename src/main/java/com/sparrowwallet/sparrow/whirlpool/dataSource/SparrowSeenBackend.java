package com.sparrowwallet.sparrow.whirlpool.dataSource;

import com.samourai.wallet.api.backend.seenBackend.ISeenBackend;
import com.samourai.wallet.api.backend.seenBackend.SeenResponse;
import com.samourai.wallet.httpClient.IHttpClient;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public class SparrowSeenBackend implements ISeenBackend {
    private IHttpClient httpClient;

    public SparrowSeenBackend(IHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public SeenResponse seen(Collection<String> addresses) throws Exception {
        Map<String,Boolean> map = new LinkedHashMap<>();
        for (String address : addresses) {
            map.put(address, seen(address));
        }
        return new SeenResponse(map);
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
