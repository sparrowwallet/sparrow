package com.sparrowwallet.sparrow.whirlpool.tor;

import com.google.common.net.HostAndPort;
import com.samourai.tor.client.TorClientService;
import com.sparrowwallet.sparrow.net.TorUtils;
import com.sparrowwallet.sparrow.whirlpool.Whirlpool;

public class SparrowTorClientService extends TorClientService {
    private final Whirlpool whirlpool;

    public SparrowTorClientService(Whirlpool whirlpool) {
        this.whirlpool = whirlpool;
    }

    @Override
    public void changeIdentity() {
        HostAndPort proxy = whirlpool.getTorProxy();
        if(proxy != null) {
            TorUtils.changeIdentity(proxy);
        }
    }
}
