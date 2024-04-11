package com.sparrowwallet.sparrow.whirlpool.dataSource;

import com.samourai.wallet.api.backend.seenBackend.ISeenBackend;
import com.samourai.wallet.api.backend.seenBackend.SeenResponse;
import com.samourai.wallet.httpClient.IHttpClient;
import com.sparrowwallet.drongo.address.Address;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.drongo.wallet.WalletNode;
import com.sparrowwallet.sparrow.AppServices;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SparrowSeenBackend implements ISeenBackend {
    private final String walletId;
    private final IHttpClient httpClient;

    public SparrowSeenBackend(String walletId, IHttpClient httpClient) {
        this.walletId = walletId;
        this.httpClient = httpClient;
    }

    @Override
    public SeenResponse seen(Collection<String> addresses) throws Exception {
        Wallet wallet = AppServices.get().getWallet(walletId);
        Map<Address, WalletNode> addressMap = wallet.getWalletAddresses();
        for(Wallet childWallet : wallet.getChildWallets()) {
            if(!childWallet.isNested()) {
                addressMap.putAll(childWallet.getWalletAddresses());
            }
        }

        Map<String,Boolean> map = new LinkedHashMap<>();
        for(String address : addresses) {
            WalletNode walletNode = addressMap.get(Address.fromString(address));
            if(walletNode != null) {
                int highestUsedIndex = walletNode.getWallet().getNode(walletNode.getKeyPurpose()).getHighestUsedIndex();
                map.put(address, walletNode.getIndex() <= highestUsedIndex);
            }
        }

        return new SeenResponse(map);
    }

    @Override
    public boolean seen(String address) throws Exception {
        SeenResponse seenResponse = seen(List.of(address));
        return seenResponse.isSeen(address);
    }

    @Override
    public IHttpClient getHttpClient() {
        return httpClient;
    }
}
