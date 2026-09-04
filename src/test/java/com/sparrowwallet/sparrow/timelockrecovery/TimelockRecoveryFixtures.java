package com.sparrowwallet.sparrow.timelockrecovery;

import com.sparrowwallet.drongo.KeyPurpose;
import com.sparrowwallet.drongo.address.Address;
import com.sparrowwallet.drongo.address.P2WPKHAddress;
import com.sparrowwallet.drongo.crypto.ChildNumber;
import com.sparrowwallet.drongo.policy.Policy;
import com.sparrowwallet.drongo.policy.PolicyType;
import com.sparrowwallet.drongo.protocol.Script;
import com.sparrowwallet.drongo.protocol.ScriptType;
import com.sparrowwallet.drongo.protocol.Sha256Hash;
import com.sparrowwallet.drongo.protocol.Transaction;
import com.sparrowwallet.drongo.wallet.BlockTransaction;
import com.sparrowwallet.drongo.wallet.BlockTransactionHashIndex;
import com.sparrowwallet.drongo.wallet.DeterministicSeed;
import com.sparrowwallet.drongo.wallet.Keystore;
import com.sparrowwallet.drongo.wallet.MnemonicException;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.drongo.wallet.WalletNode;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class TimelockRecoveryFixtures {
    public static final String WORDS = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about";
    public static final String WORDS_2 = "legal winner thank year wave sausage worth useful legal winner thank yellow";
    public static final String DESTINATION = "bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4";

    private TimelockRecoveryFixtures() {
    }

    public static Wallet fundedWallet(ScriptType scriptType, PolicyType policyType) throws MnemonicException {
        return fundedWallet(scriptType, policyType, 1, 1_000_000L);
    }

    public static Wallet fundedWallet(ScriptType scriptType, PolicyType policyType, int utxoCount, long satsEach) throws MnemonicException {
        DeterministicSeed seed = new DeterministicSeed(WORDS, "", 0, DeterministicSeed.Type.BIP39);
        Wallet wallet = new Wallet("Timelock Test");
        wallet.setPolicyType(policyType);
        wallet.setScriptType(scriptType);
        List<ChildNumber> derivation = scriptType.getDefaultDerivation();
        Keystore keystore = Keystore.fromSeed(seed, policyType, derivation);
        wallet.getKeystores().add(keystore);
        wallet.setDefaultPolicy(Policy.getPolicy(policyType, scriptType, wallet.getKeystores(), 1));
        wallet.setStoredBlockHeight(850010);
        fundWallet(wallet, utxoCount, satsEach);
        return wallet;
    }

    public static Wallet fundedP2wshMultisig() throws MnemonicException {
        Wallet wallet = new Wallet("Timelock Multisig");
        wallet.setPolicyType(PolicyType.MULTI_HD);
        wallet.setScriptType(ScriptType.P2WSH);
        DeterministicSeed seed1 = new DeterministicSeed(WORDS, "", 0, DeterministicSeed.Type.BIP39);
        DeterministicSeed seed2 = new DeterministicSeed(WORDS_2, "", 0, DeterministicSeed.Type.BIP39);
        wallet.getKeystores().add(Keystore.fromSeed(seed1, PolicyType.MULTI_HD, ScriptType.P2WSH.getDefaultDerivation()));
        wallet.getKeystores().add(Keystore.fromSeed(seed2, PolicyType.MULTI_HD, ScriptType.P2WSH.getDefaultDerivation()));
        wallet.getKeystores().get(0).setLabel("Keystore 1");
        wallet.getKeystores().get(1).setLabel("Keystore 2");
        wallet.setDefaultPolicy(Policy.getPolicy(PolicyType.MULTI_HD, ScriptType.P2WSH, wallet.getKeystores(), 2));
        wallet.setStoredBlockHeight(850010);
        fundWallet(wallet, 1, 2_000_000L);
        return wallet;
    }

    public static void fundWallet(Wallet wallet, int utxoCount, long satsEach) {
        Map<Sha256Hash, BlockTransaction> txs = new LinkedHashMap<>();
        Date fundingDate = new Date(1700000000000L);
        WalletNode node = wallet.getFreshNode(KeyPurpose.RECEIVE);
        for(int i = 0; i < utxoCount; i++) {
            if(i > 0) {
                node = wallet.getFreshNode(KeyPurpose.RECEIVE, node);
            }
            Transaction fundingTx = new Transaction();
            fundingTx.addInput(Sha256Hash.wrap(String.format("%064x", i + 1)), 0, new Script(new byte[0]));
            fundingTx.addOutput(satsEach, node.getAddress());
            BlockTransaction fundingBlkTx = new BlockTransaction(fundingTx.getTxId(), 850000, fundingDate, null, fundingTx);
            txs.put(fundingTx.getTxId(), fundingBlkTx);
            node.getTransactionOutputs().add(new BlockTransactionHashIndex(fundingTx.getTxId(), 850000, fundingDate, null, 0, satsEach));
        }
        wallet.updateTransactions(txs);
    }

    public static Address destination() throws Exception {
        return Address.fromString(DESTINATION);
    }

    public static Address externalAddress(int index) {
        byte[] hash = new byte[20];
        hash[0] = (byte)0xAB;
        hash[1] = (byte)(index >> 8);
        hash[2] = (byte)index;
        hash[19] = (byte)(index * 17);
        return new P2WPKHAddress(hash);
    }

    public static List<TimelockRecoveryRecipient> manyDestinations(int count) {
        List<TimelockRecoveryRecipient> recipients = new ArrayList<>();
        for(int i = 0; i < count - 1; i++) {
            recipients.add(TimelockRecoveryRecipient.of(externalAddress(i), "Heir " + i, 1_000L));
        }
        recipients.add(TimelockRecoveryRecipient.remaining(externalAddress(count - 1), "Remainder"));
        return recipients;
    }

    public static TimelockRecovery signedRecovery(Wallet wallet, List<TimelockRecoveryRecipient> recipients,
                                           int days, double feeRate, boolean includeCancellation) throws Exception {
        TimelockRecovery recovery = TimelockRecovery.create(wallet, recipients, days, feeRate, includeCancellation, 850010);
        wallet.sign(recovery.getInitiationPsbt());
        recovery.extractSignedInitiation();
        wallet.sign(recovery.getRecoveryPsbt());
        recovery.extractSignedRecovery();
        if(includeCancellation) {
            wallet.sign(recovery.getCancellationPsbt());
            recovery.extractSignedCancellation();
        }
        return recovery;
    }
}
