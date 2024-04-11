package com.sparrowwallet.sparrow.soroban;

import com.samourai.soroban.client.SorobanConfig;
import com.samourai.soroban.client.wallet.SorobanWalletService;
import com.samourai.wallet.hd.HD_Wallet;
import com.samourai.wallet.util.ExtLibJConfig;
import com.sparrowwallet.drongo.Network;
import com.sparrowwallet.drongo.protocol.ScriptType;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.whirlpool.Whirlpool;
import com.sparrowwallet.sparrow.whirlpool.dataSource.SparrowChainSupplier;
import org.bitcoinj.core.NetworkParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class Soroban {
    private static final Logger log = LoggerFactory.getLogger(Soroban.class);

    protected static final int TIMEOUT_MS = 60000;
    public static final List<Network> SOROBAN_NETWORKS = List.of(Network.MAINNET, Network.TESTNET);

    private final SorobanWalletService sorobanWalletService;

    private HD_Wallet hdWallet;
    private int bip47Account;
    private SparrowChainSupplier chainSupplier;

    public Soroban() {
        SorobanConfig sorobanConfig = AppServices.getWhirlpoolServices().getSorobanConfig();
        this.sorobanWalletService = sorobanConfig.getSorobanWalletService();
    }

    public HD_Wallet getHdWallet() {
        return hdWallet;
    }

    public void setHDWallet(Wallet wallet) {
        ExtLibJConfig extLibJConfig = sorobanWalletService.getSorobanService().getSorobanConfig().getExtLibJConfig();
        NetworkParameters params = extLibJConfig.getSamouraiNetwork().getParams();
        hdWallet = Whirlpool.computeHdWallet(wallet, params);
        bip47Account = wallet.isMasterWallet() ? wallet.getAccountIndex() : wallet.getMasterWallet().getAccountIndex();
    }

    public SparrowCahootsWallet getCahootsWallet(Wallet wallet) {
        if(wallet.getScriptType() != ScriptType.P2WPKH) {
            throw new IllegalArgumentException("Wallet must be P2WPKH");
        }

        if(hdWallet == null) {
            for(Wallet associatedWallet : wallet.getAllWallets()) {
                Soroban soroban = AppServices.getSorobanServices().getSoroban(associatedWallet);
                if(soroban != null && soroban.getHdWallet() != null) {
                    hdWallet = soroban.hdWallet;
                }
            }
        }

        if(hdWallet == null) {
            throw new IllegalStateException("HD wallet is not set");
        }

        try {
            if(chainSupplier == null) {
                chainSupplier = new SparrowChainSupplier(wallet.getStoredBlockHeight());
                chainSupplier.open();
            }
            return new SparrowCahootsWallet(chainSupplier, wallet, hdWallet, bip47Account);
        } catch(Exception e) {
            log.error("Could not create cahoots wallet", e);
        }

        return null;
    }

    public int getBip47Account() {
        return bip47Account;
    }

    public SorobanWalletService getSorobanWalletService() {
        return sorobanWalletService;
    }

    public void close() {
        if(chainSupplier != null) {
            chainSupplier.close();
        }
    }
}
