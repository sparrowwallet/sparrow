package com.sparrowwallet.sparrow.net.cormorant.bitcoind;

import com.github.arteam.simplejsonrpc.client.JsonRpcParams;
import com.github.arteam.simplejsonrpc.client.ParamsType;
import com.github.arteam.simplejsonrpc.core.annotation.JsonRpcMethod;
import com.github.arteam.simplejsonrpc.core.annotation.JsonRpcOptional;
import com.github.arteam.simplejsonrpc.core.annotation.JsonRpcParam;
import com.github.arteam.simplejsonrpc.core.annotation.JsonRpcService;
import com.sparrowwallet.drongo.protocol.Sha256Hash;

import java.util.List;
import java.util.Map;
import java.util.Set;

@JsonRpcService
@JsonRpcParams(ParamsType.ARRAY)
public interface BitcoindClientService {
    @JsonRpcMethod("uptime")
    long uptime();

    @JsonRpcMethod("getnetworkinfo")
    NetworkInfo getNetworkInfo();

    @JsonRpcMethod("estimatesmartfee")
    FeeInfo estimateSmartFee(@JsonRpcParam("conf_target") int blocks);

    @JsonRpcMethod("getrawmempool")
    Set<Sha256Hash> getRawMempool();

    @JsonRpcMethod("getrawmempool")
    Map<Sha256Hash, MempoolEntry> getRawMempool(@JsonRpcParam("verbose") boolean verbose);

    @JsonRpcMethod("getmempoolinfo")
    MempoolInfo getMempoolInfo();

    @JsonRpcMethod("getblockchaininfo")
    BlockchainInfo getBlockchainInfo();

    @JsonRpcMethod("getwalletinfo")
    WalletInfo getWalletInfo();

    @JsonRpcMethod("getblockhash")
    String getBlockHash(@JsonRpcParam("height") int height);

    @JsonRpcMethod("getblockheader")
    String getBlockHeader(@JsonRpcParam("blockhash") String blockhash, @JsonRpcParam("verbose") boolean verbose);

    @JsonRpcMethod("getblockheader")
    VerboseBlockHeader getBlockHeader(@JsonRpcParam("blockhash") String blockhash);

    @JsonRpcMethod("getrawtransaction")
    Object getRawTransaction(@JsonRpcParam("txid") String txid, @JsonRpcParam("verbose") boolean verbose);

    @JsonRpcMethod("gettransaction")
    Map<String, Object> getTransaction(@JsonRpcParam("txid") String txid, @JsonRpcParam("include_watchonly") boolean includeWatchOnly, @JsonRpcParam("verbose") boolean verbose);

    @JsonRpcMethod("getmempoolentry")
    MempoolEntry getMempoolEntry(@JsonRpcParam("txid") String txid);

    @JsonRpcMethod("listsinceblock")
    ListSinceBlock listSinceBlock(@JsonRpcParam("blockhash") @JsonRpcOptional String blockhash, @JsonRpcParam("target_confirmations") int targetConfirmations,
                                  @JsonRpcParam("include_watchonly") boolean includeWatchOnly, @JsonRpcParam("include_removed") boolean includeRemoved,
                                  @JsonRpcParam("include_change") boolean includeChange);

    @JsonRpcMethod("listwalletdir")
    ListWalletDirResult listWalletDir();

    @JsonRpcMethod("listwallets")
    List<String> listWallets();

    @JsonRpcMethod("createwallet")
    CreateLoadWalletResult createWallet(@JsonRpcParam("wallet_name") String name, @JsonRpcParam("disable_private_keys") boolean disablePrivateKeys, @JsonRpcParam("blank") boolean blank,
                                        @JsonRpcParam("passphrase") String passphrase, @JsonRpcParam("avoid_reuse") boolean avoidReuse, @JsonRpcParam("descriptors") boolean descriptors,
                                        @JsonRpcParam("load_on_startup") boolean loadOnStartup, @JsonRpcParam("external_signer") boolean externalSigner);

    @JsonRpcMethod("loadwallet")
    CreateLoadWalletResult loadWallet(@JsonRpcParam("filename") String name, @JsonRpcParam("load_on_startup") boolean loadOnStartup);

    @JsonRpcMethod("unloadwallet")
    CreateLoadWalletResult unloadWallet(@JsonRpcParam("wallet_name") String name, @JsonRpcParam("load_on_startup") boolean loadOnStartup);

    @JsonRpcMethod("listdescriptors")
    ListDescriptorsResult listDescriptors(@JsonRpcParam("private") boolean listPrivate);

    @JsonRpcMethod("importdescriptors")
    List<ImportDescriptorResult> importDescriptors(@JsonRpcParam("requests") List<ImportDescriptor> importDescriptors);

    @JsonRpcMethod("sendrawtransaction")
    String sendRawTransaction(@JsonRpcParam("hexstring") String rawTx, @JsonRpcParam("maxfeerate") Double maxFeeRate);
}
