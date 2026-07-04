package com.sparrowwallet.sparrow.io;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sparrowwallet.drongo.ExtendedKey;
import com.sparrowwallet.drongo.KeyDerivation;
import com.sparrowwallet.drongo.KeyPurpose;
import com.sparrowwallet.drongo.Network;
import com.sparrowwallet.drongo.address.Address;
import com.sparrowwallet.drongo.policy.Policy;
import com.sparrowwallet.drongo.policy.PolicyType;
import com.sparrowwallet.drongo.protocol.Script;
import com.sparrowwallet.drongo.protocol.ScriptType;
import com.sparrowwallet.drongo.protocol.Sha256Hash;
import com.sparrowwallet.drongo.protocol.Transaction;
import com.sparrowwallet.drongo.wallet.*;
import com.sparrowwallet.sparrow.SparrowWallet;
import com.sparrowwallet.sparrow.wallet.WalletForm;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class WalletLabelsTest {
    //BIP84 test vector account key at m/84'/0'/0'
    private static final String XPUB = "xpub6CatWdiZiodmUeTDp8LT5or8nmbKNcuyvz7WyksVFkKB4RHwCD3XyuvPEbvqAQY3rAPshWcMLoP2fMFMKHPJ4ZeZXYVUhLv1VMrjPC7PW6V";
    private static final String MASTER_FINGERPRINT = "73c5da0a";
    private static final String ORIGIN = "wpkh([73c5da0a/84h/0h/0h])";

    @TempDir
    private static Path tempHome;

    @BeforeAll
    public static void setup() {
        //Isolate Config.get() from the developer's config, whose settings can otherwise change entry construction (e.g. hiding empty used addresses)
        System.setProperty(SparrowWallet.APP_HOME_PROPERTY, tempHome.toString());
        Network.set(Network.MAINNET);
    }

    @AfterAll
    public static void tearDown() {
        System.clearProperty(SparrowWallet.APP_HOME_PROPERTY);
    }

    @Test
    public void testExport() throws Exception {
        TestWallet testWallet = createTestWallet();
        applyLabels(testWallet);
        Assertions.assertEquals("bc1qcr8te4kr609gcawutmrza0j4xv80jy8z306fyu", testWallet.receiveNode0.getAddress().toString());

        WalletLabels walletLabels = new WalletLabels(List.of(testWallet.walletForm));
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        walletLabels.exportWallet(testWallet.wallet, outputStream, null);

        Map<String, JsonObject> labels = parseLabels(outputStream);
        String fundingTxid = testWallet.fundingBlkTx.getHashAsString();
        String spendingTxid = testWallet.spendingBlkTx.getHashAsString();

        JsonObject xpubLabel = labels.get("xpub:" + testWallet.wallet.getKeystores().get(0).getExtendedPublicKey().toString());
        Assertions.assertEquals("Test Keystore", xpubLabel.get("label").getAsString());

        JsonObject fundingTxLabel = labels.get("tx:" + fundingTxid);
        Assertions.assertEquals("Funding transaction", fundingTxLabel.get("label").getAsString());
        Assertions.assertEquals(ORIGIN, fundingTxLabel.get("origin").getAsString());
        Assertions.assertEquals(850000, fundingTxLabel.get("height").getAsInt());
        Assertions.assertEquals("2023-11-14T22:13:20Z", fundingTxLabel.get("time").getAsString());
        Assertions.assertEquals(300000L, fundingTxLabel.get("value").getAsLong());
        Assertions.assertFalse(fundingTxLabel.has("fee"));

        JsonObject spendingTxLabel = labels.get("tx:" + spendingTxid);
        Assertions.assertEquals("Spending transaction", spendingTxLabel.get("label").getAsString());
        Assertions.assertEquals(850001, spendingTxLabel.get("height").getAsInt());
        Assertions.assertEquals(10000L, spendingTxLabel.get("fee").getAsLong());
        Assertions.assertEquals(-100000L, spendingTxLabel.get("value").getAsLong());

        JsonObject addr0Label = labels.get("addr:" + testWallet.receiveNode0.getAddress());
        Assertions.assertEquals("Primary address", addr0Label.get("label").getAsString());
        Assertions.assertEquals("/0/0", addr0Label.get("keypath").getAsString());
        List<Integer> addr0Heights = addr0Label.get("heights").getAsJsonArray().asList().stream().map(JsonElement::getAsInt).toList();
        Assertions.assertEquals(List.of(850000, 850001), addr0Heights);

        JsonObject addr1Label = labels.get("addr:" + testWallet.receiveNode1.getAddress());
        Assertions.assertEquals("Savings address", addr1Label.get("label").getAsString());
        Assertions.assertEquals("/0/1", addr1Label.get("keypath").getAsString());
        Assertions.assertEquals(List.of(850000), addr1Label.get("heights").getAsJsonArray().asList().stream().map(JsonElement::getAsInt).toList());

        JsonObject spentOutputLabel = labels.get("output:" + fundingTxid + ":0");
        Assertions.assertEquals("Received coins", spentOutputLabel.get("label").getAsString());
        Assertions.assertEquals("/0/0", spentOutputLabel.get("keypath").getAsString());
        Assertions.assertEquals(100000L, spentOutputLabel.get("value").getAsLong());
        Assertions.assertFalse(spentOutputLabel.has("spendable"), "Spendable should be omitted for a spent output");

        JsonObject frozenOutputLabel = labels.get("output:" + fundingTxid + ":1");
        Assertions.assertEquals("Frozen coins", frozenOutputLabel.get("label").getAsString());
        Assertions.assertEquals(200000L, frozenOutputLabel.get("value").getAsLong());
        Assertions.assertFalse(frozenOutputLabel.get("spendable").getAsBoolean(), "Spendable should be false for a frozen output");

        JsonObject inputLabel = labels.get("input:" + spendingTxid + ":0");
        Assertions.assertEquals("Spent input", inputLabel.get("label").getAsString());
        Assertions.assertEquals("/0/0", inputLabel.get("keypath").getAsString());
        Assertions.assertEquals(100000L, inputLabel.get("value").getAsLong());
        Assertions.assertEquals(850001, inputLabel.get("height").getAsInt());
    }

    @Test
    public void testImport() throws Exception {
        TestWallet testWallet = createTestWallet();
        testWallet.fundingTxo1.setStatus(Status.FROZEN);

        String fundingTxid = testWallet.fundingBlkTx.getHashAsString();
        String spendingTxid = testWallet.spendingBlkTx.getHashAsString();
        String jsonl = String.join("\n",
                "{\"type\":\"tx\",\"ref\":\"" + fundingTxid + "\",\"label\":\"Funding transaction\",\"origin\":\"wpkh([73c5da0a/84h/0h/0h])\"}",
                "{\"type\":\"tx\",\"ref\":\"" + spendingTxid + "\",\"label\":\"Wrong origin\",\"origin\":\"wpkh([00000001/84h/0h/0h])\"}",
                "{\"type\":\"addr\",\"ref\":\"" + testWallet.receiveNode0.getAddress() + "\",\"label\":\"Primary address\",\"origin\":\"wpkh([73C5DA0A/84'/0'/0'])\"}",
                "{\"type\":\"output\",\"ref\":\"" + fundingTxid + ":1\",\"spendable\":true}",
                "{\"type\":\"output\",\"ref\":\"" + fundingTxid + ":0\",\"label\":\"Received coins\"}",
                "{\"type\":\"input\",\"ref\":\"" + spendingTxid + ":0\",\"label\":\"Spent input\"}",
                "this line is not valid json or csv",
                spendingTxid + ",Spending transaction");

        WalletLabels walletLabels = new WalletLabels(List.of(testWallet.walletForm));
        Wallet imported = walletLabels.importWallet(new ByteArrayInputStream(jsonl.getBytes(StandardCharsets.UTF_8)), null);

        Assertions.assertEquals(testWallet.wallet, imported);
        Assertions.assertEquals("Funding transaction", testWallet.fundingBlkTx.getLabel());
        Assertions.assertEquals("Spending transaction", testWallet.spendingBlkTx.getLabel(), "CSV fallback label should be applied, mismatched origin label should not");
        Assertions.assertEquals("Primary address", testWallet.receiveNode0.getLabel());
        Assertions.assertEquals("Received coins", testWallet.fundingTxo0.getLabel());
        Assertions.assertEquals("Spent input", testWallet.spendingTxi.getLabel());
        Assertions.assertNull(testWallet.fundingTxo1.getStatus(), "Spendable true should thaw a frozen output");
    }

    @Test
    public void testRoundTrip() throws Exception {
        TestWallet sourceWallet = createTestWallet();
        applyLabels(sourceWallet);

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        new WalletLabels(List.of(sourceWallet.walletForm)).exportWallet(sourceWallet.wallet, outputStream, null);

        //Importing an xpub label requires the AppServices singleton, which is not available outside the UI
        String exported = outputStream.toString(StandardCharsets.UTF_8).lines().filter(line -> !line.contains("\"type\":\"xpub\"")).collect(Collectors.joining("\n"));

        TestWallet destinationWallet = createTestWallet();
        Assertions.assertNull(destinationWallet.fundingBlkTx.getLabel());
        new WalletLabels(List.of(destinationWallet.walletForm)).importWallet(new ByteArrayInputStream(exported.getBytes(StandardCharsets.UTF_8)), null);

        Assertions.assertEquals("Funding transaction", destinationWallet.fundingBlkTx.getLabel());
        Assertions.assertEquals("Spending transaction", destinationWallet.spendingBlkTx.getLabel());
        Assertions.assertEquals("Primary address", destinationWallet.receiveNode0.getLabel());
        Assertions.assertEquals("Savings address", destinationWallet.receiveNode1.getLabel());
        Assertions.assertEquals("Received coins", destinationWallet.fundingTxo0.getLabel());
        Assertions.assertEquals("Frozen coins", destinationWallet.fundingTxo1.getLabel());
        Assertions.assertEquals("Spent input", destinationWallet.spendingTxi.getLabel());
        Assertions.assertEquals(Status.FROZEN, destinationWallet.fundingTxo1.getStatus(), "Frozen status should be restored on import");
        Assertions.assertNull(destinationWallet.fundingTxo0.getStatus());
    }

    private void applyLabels(TestWallet testWallet) {
        testWallet.fundingBlkTx.setLabel("Funding transaction");
        testWallet.spendingBlkTx.setLabel("Spending transaction");
        testWallet.receiveNode0.setLabel("Primary address");
        testWallet.receiveNode1.setLabel("Savings address");
        testWallet.fundingTxo0.setLabel("Received coins");
        testWallet.fundingTxo1.setLabel("Frozen coins");
        testWallet.spendingTxi.setLabel("Spent input");
        testWallet.fundingTxo1.setStatus(Status.FROZEN);
    }

    private Map<String, JsonObject> parseLabels(ByteArrayOutputStream outputStream) {
        return outputStream.toString(StandardCharsets.UTF_8).lines().map(line -> JsonParser.parseString(line).getAsJsonObject())
                .collect(Collectors.toMap(json -> json.get("type").getAsString() + ":" + json.get("ref").getAsString(), json -> json));
    }

    private TestWallet createTestWallet() throws Exception {
        Wallet wallet = new Wallet("Labels Test");
        wallet.setPolicyType(PolicyType.SINGLE_HD);
        wallet.setScriptType(ScriptType.P2WPKH);

        Keystore keystore = new Keystore("Test Keystore");
        keystore.setSource(KeystoreSource.SW_WATCH);
        keystore.setWalletModel(WalletModel.SPARROW);
        //Use hardened notation to ensure origin matching normalizes the wallet-side derivation
        keystore.setKeyDerivation(new KeyDerivation(MASTER_FINGERPRINT, "m/84h/0h/0h"));
        keystore.setExtendedPublicKey(ExtendedKey.fromDescriptor(XPUB));
        wallet.getKeystores().add(keystore);
        wallet.setDefaultPolicy(Policy.getPolicy(PolicyType.SINGLE_HD, ScriptType.P2WPKH, wallet.getKeystores(), null));
        wallet.setStoredBlockHeight(850010);
        Assertions.assertTrue(wallet.isValid());

        Iterator<WalletNode> receiveNodes = wallet.getNode(KeyPurpose.RECEIVE).getChildren().iterator();
        WalletNode receiveNode0 = receiveNodes.next();
        WalletNode receiveNode1 = receiveNodes.next();

        Date fundingDate = new Date(1700000000000L);
        Transaction fundingTx = new Transaction();
        fundingTx.addInput(Sha256Hash.wrap("0000000000000000000000000000000000000000000000000000000000000001"), 0, new Script(new byte[0]));
        fundingTx.addOutput(100000L, receiveNode0.getAddress());
        fundingTx.addOutput(200000L, receiveNode1.getAddress());
        BlockTransaction fundingBlkTx = new BlockTransaction(fundingTx.getTxId(), 850000, fundingDate, null, fundingTx);

        Date spendingDate = new Date(1700086400000L);
        Transaction spendingTx = new Transaction();
        spendingTx.addInput(fundingTx.getTxId(), 0, new Script(new byte[0]));
        spendingTx.addOutput(90000L, Address.fromString("bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4"));
        BlockTransaction spendingBlkTx = new BlockTransaction(spendingTx.getTxId(), 850001, spendingDate, null, spendingTx);

        wallet.updateTransactions(Map.of(fundingTx.getTxId(), fundingBlkTx, spendingTx.getTxId(), spendingBlkTx));

        BlockTransactionHashIndex fundingTxo0 = new BlockTransactionHashIndex(fundingTx.getTxId(), 850000, fundingDate, null, 0, 100000L);
        BlockTransactionHashIndex spendingTxi = new BlockTransactionHashIndex(spendingTx.getTxId(), 850001, spendingDate, null, 0, 100000L);
        fundingTxo0.setSpentBy(spendingTxi);
        receiveNode0.getTransactionOutputs().add(fundingTxo0);

        BlockTransactionHashIndex fundingTxo1 = new BlockTransactionHashIndex(fundingTx.getTxId(), 850000, fundingDate, null, 1, 200000L);
        receiveNode1.getTransactionOutputs().add(fundingTxo1);

        Storage storage = new Storage(PersistenceType.JSON, new File(tempHome.toFile(), "labels-test.json"));
        WalletForm walletForm = new WalletForm(storage, wallet);

        return new TestWallet(walletForm, wallet, receiveNode0, receiveNode1, fundingBlkTx, spendingBlkTx, fundingTxo0, fundingTxo1, spendingTxi);
    }

    private static class TestWallet {
        final WalletForm walletForm;
        final Wallet wallet;
        final WalletNode receiveNode0;
        final WalletNode receiveNode1;
        final BlockTransaction fundingBlkTx;
        final BlockTransaction spendingBlkTx;
        final BlockTransactionHashIndex fundingTxo0;
        final BlockTransactionHashIndex fundingTxo1;
        final BlockTransactionHashIndex spendingTxi;

        TestWallet(WalletForm walletForm, Wallet wallet, WalletNode receiveNode0, WalletNode receiveNode1, BlockTransaction fundingBlkTx, BlockTransaction spendingBlkTx,
                   BlockTransactionHashIndex fundingTxo0, BlockTransactionHashIndex fundingTxo1, BlockTransactionHashIndex spendingTxi) {
            this.walletForm = walletForm;
            this.wallet = wallet;
            this.receiveNode0 = receiveNode0;
            this.receiveNode1 = receiveNode1;
            this.fundingBlkTx = fundingBlkTx;
            this.spendingBlkTx = spendingBlkTx;
            this.fundingTxo0 = fundingTxo0;
            this.fundingTxo1 = fundingTxo1;
            this.spendingTxi = spendingTxi;
        }
    }
}
