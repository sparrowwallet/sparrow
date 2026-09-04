package com.sparrowwallet.sparrow.timelockrecovery;

import com.sparrowwallet.drongo.KeyPurpose;
import com.sparrowwallet.drongo.Network;
import com.sparrowwallet.drongo.address.Address;
import com.sparrowwallet.drongo.policy.PolicyType;
import com.sparrowwallet.drongo.protocol.ScriptType;
import com.sparrowwallet.drongo.protocol.Transaction;
import com.sparrowwallet.drongo.protocol.TransactionInput;
import com.sparrowwallet.drongo.wallet.Status;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.drongo.wallet.WalletNode;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.sparrowwallet.sparrow.timelockrecovery.TimelockRecoveryFixtures.DESTINATION;
import static com.sparrowwallet.sparrow.timelockrecovery.TimelockRecoveryFixtures.destination;
import static com.sparrowwallet.sparrow.timelockrecovery.TimelockRecoveryFixtures.fundedP2wshMultisig;
import static com.sparrowwallet.sparrow.timelockrecovery.TimelockRecoveryFixtures.fundedWallet;

public class TimelockRecoveryTest {
    @BeforeAll
    public static void setup() {
        Network.set(Network.MAINNET);
    }

    @AfterAll
    public static void tearDown() {
        Network.set(Network.MAINNET);
    }

    @Test
    public void relativeTimelockSequence90Days() throws Exception {
        Assertions.assertEquals(0x00403B54L, TimelockRecovery.relativeTimelockSequence(90));
    }

    @Test
    public void relativeTimelockSequenceBounds() {
        Assertions.assertThrows(TimelockRecoveryException.class, () -> TimelockRecovery.relativeTimelockSequence(1));
        Assertions.assertThrows(TimelockRecoveryException.class, () -> TimelockRecovery.relativeTimelockSequence(389));
        Assertions.assertDoesNotThrow(() -> TimelockRecovery.relativeTimelockSequence(2));
        Assertions.assertDoesNotThrow(() -> TimelockRecovery.relativeTimelockSequence(388));
    }

    @Test
    public void eligibility() throws Exception {
        Wallet p2wpkh = fundedWallet(ScriptType.P2WPKH, PolicyType.SINGLE_HD);
        Assertions.assertTrue(TimelockRecovery.isEligible(p2wpkh));

        Wallet p2pkh = fundedWallet(ScriptType.P2PKH, PolicyType.SINGLE_HD);
        Assertions.assertFalse(TimelockRecovery.isEligible(p2pkh));

        Wallet p2tr = fundedWallet(ScriptType.P2TR, PolicyType.SINGLE_HD);
        Assertions.assertFalse(TimelockRecovery.isEligible(p2tr));

        Wallet nested = fundedWallet(ScriptType.P2SH_P2WPKH, PolicyType.SINGLE_HD);
        Assertions.assertFalse(TimelockRecovery.isEligible(nested));

        Wallet p2wsh = fundedP2wshMultisig();
        Assertions.assertTrue(TimelockRecovery.isEligible(p2wsh));
        Assertions.assertTrue(p2wsh.isValid());

        Assertions.assertFalse(TimelockRecovery.isEligible(null));
        Assertions.assertFalse(TimelockRecovery.isEligible(new Wallet()));
    }

    @Test
    public void constructsInitiationRecoveryAndCancellation() throws Exception {
        Wallet wallet = fundedWallet(ScriptType.P2WPKH, PolicyType.SINGLE_HD);
        Address destination = destination();
        TimelockRecovery recovery = TimelockRecovery.create(wallet,
                List.of(TimelockRecoveryRecipient.remaining(destination, "Backup")),
                90, 1.0, true, 850010);

        Assertions.assertEquals(0x00403B54L, recovery.getRelativeSequence());
        Assertions.assertEquals(TransactionInput.SEQUENCE_RBF_ENABLED,
                recovery.getInitiationPsbt().getTransaction().getInputs().get(0).getSequenceNumber());

        Transaction initiationTx = recovery.getInitiationPsbt().getTransaction();
        Assertions.assertEquals(2, initiationTx.getVersion());
        long anchorOutputs = initiationTx.getOutputs().stream()
                .filter(output -> output.getValue() == TimelockRecovery.ANCHOR_AMOUNT_SATS).count();
        Assertions.assertEquals(1, anchorOutputs);
        Assertions.assertEquals(destination, initiationTx.getOutputs().stream()
                .filter(output -> output.getValue() == TimelockRecovery.ANCHOR_AMOUNT_SATS)
                .findFirst().orElseThrow().getScript().getToAddress());
        Assertions.assertEquals(recovery.getInitiationAddress(),
                initiationTx.getOutputs().get(recovery.getInitiationVout()).getScript().getToAddress());
        Assertions.assertNotEquals(TimelockRecovery.ANCHOR_AMOUNT_SATS, recovery.getInitiationWalletTx().getTransaction().getOutputs().stream()
                .filter(output -> recovery.getInitiationAddress().equals(output.getScript().getToAddress()))
                .findFirst().orElseThrow().getValue());

        Transaction recoveryTx = recovery.getRecoveryPsbt().getTransaction();
        Assertions.assertEquals(1, recoveryTx.getInputs().size());
        Assertions.assertEquals(recovery.getInitiationTxid(), recoveryTx.getInputs().get(0).getOutpoint().getHash());
        Assertions.assertEquals(recovery.getInitiationVout(), recoveryTx.getInputs().get(0).getOutpoint().getIndex());
        Assertions.assertEquals(0x00403B54L, recoveryTx.getInputs().get(0).getSequenceNumber());
        Assertions.assertEquals(destination, recoveryTx.getOutputs().get(0).getScript().getToAddress());
        Assertions.assertTrue(recoveryTx.getOutputs().get(0).getValue() >= TimelockRecovery.ANCHOR_AMOUNT_SATS);

        Transaction cancellationTx = recovery.getCancellationPsbt().getTransaction();
        Assertions.assertEquals(TransactionInput.SEQUENCE_RBF_ENABLED, cancellationTx.getInputs().get(0).getSequenceNumber());
        Assertions.assertEquals(recovery.getCancellationAddress(), cancellationTx.getOutputs().get(0).getScript().getToAddress());
        Assertions.assertNotEquals(recovery.getInitiationAddress(), recovery.getCancellationAddress());

        Assertions.assertEquals(recovery.getInitiationTxid(), recovery.getInitiationWalletTx().getTransaction().getTxId());
        Assertions.assertEquals(recovery.getInitiationTxid(), recovery.getInitiationPsbt().getTransaction().getTxId());
        Assertions.assertEquals(TimelockRecovery.INITIATION_ADDRESS_LABEL, recovery.getInitiationNode().getLabel());
        Assertions.assertEquals(TimelockRecovery.CANCELLATION_ADDRESS_LABEL, recovery.getCancellationNode().getLabel());
    }

    @Test
    public void constructsMultipleRecoveryOutputs() throws Exception {
        Wallet wallet = fundedWallet(ScriptType.P2WPKH, PolicyType.SINGLE_HD);
        Address first = Address.fromString(DESTINATION);
        Address second = Address.fromString("bc1qar0srrr7xfkvy5l643lydnw9re59gtzzwf5mdq");
        TimelockRecovery recovery = TimelockRecovery.create(wallet,
                List.of(TimelockRecoveryRecipient.of(first, "Heir", 10_000L),
                        TimelockRecoveryRecipient.remaining(second, "Backup")),
                90, 1.0, false, 850010);

        long anchors = recovery.getInitiationPsbt().getTransaction().getOutputs().stream()
                .filter(output -> output.getValue() == TimelockRecovery.ANCHOR_AMOUNT_SATS).count();
        Assertions.assertEquals(2, anchors);
        Assertions.assertEquals(2, recovery.getRecoveryPsbt().getTransaction().getOutputs().size());
        Assertions.assertEquals(10_000L, recovery.getRecoveryPsbt().getTransaction().getOutputs().get(0).getValue());
        Assertions.assertEquals(first, recovery.getRecoveryPsbt().getTransaction().getOutputs().get(0).getScript().getToAddress());
        Assertions.assertEquals(second, recovery.getRecoveryPsbt().getTransaction().getOutputs().get(1).getScript().getToAddress());
        Assertions.assertTrue(recovery.getRecoveryPsbt().getTransaction().getOutputs().get(1).getValue() >= TimelockRecovery.ANCHOR_AMOUNT_SATS);
        Assertions.assertNull(recovery.getCancellationPsbt());
    }

    @Test
    public void signsInitiationAndRecovery() throws Exception {
        Wallet wallet = fundedWallet(ScriptType.P2WPKH, PolicyType.SINGLE_HD);
        Address destination = destination();
        TimelockRecovery recovery = TimelockRecovery.create(wallet,
                List.of(TimelockRecoveryRecipient.remaining(destination, "Backup")),
                90, 2.0, true, 850010);

        wallet.sign(recovery.getInitiationPsbt());
        Transaction signedInitiation = recovery.extractSignedInitiation();
        Assertions.assertTrue(signedInitiation.hasWitnesses());

        wallet.sign(recovery.getRecoveryPsbt());
        Transaction signedRecovery = recovery.extractSignedRecovery();
        Assertions.assertEquals(signedInitiation.getTxId(), signedRecovery.getInputs().get(0).getOutpoint().getHash());
        Assertions.assertEquals(0x00403B54L, signedRecovery.getInputs().get(0).getSequenceNumber());

        wallet.sign(recovery.getCancellationPsbt());
        Transaction signedCancellation = recovery.extractSignedCancellation();
        Assertions.assertEquals(TransactionInput.SEQUENCE_RBF_ENABLED, signedCancellation.getInputs().get(0).getSequenceNumber());

        TimelockRecoveryPlan plan = TimelockRecoveryPlan.from(recovery);
        Assertions.assertEquals("timelock-recovery-plan", plan.getFields().get("kind"));
        Assertions.assertEquals(8, plan.getChecksum().length());
        Assertions.assertEquals(plan.getChecksum(), Bip128Json.checksum(plan.getFields()));

        TimelockCancellationPlan cancellationPlan = TimelockCancellationPlan.from(recovery);
        Assertions.assertEquals("timelock-cancellation-plan", cancellationPlan.getFields().get("kind"));
        Assertions.assertEquals(cancellationPlan.getChecksum(), Bip128Json.checksum(cancellationPlan.getFields()));
    }

    @Test
    public void splitHexForQr() {
        Assertions.assertEquals(List.of("ABCD"), TimelockRecovery.splitHexForQr("ABCD"));
        Assertions.assertEquals(1, TimelockRecovery.splitHexForQr("A".repeat(TimelockRecovery.QR_HEX_CHUNK_SIZE)).size());
        List<String> parts = TimelockRecovery.splitHexForQr("B".repeat(TimelockRecovery.QR_HEX_CHUNK_SIZE + 1));
        Assertions.assertEquals(2, parts.size());
        Assertions.assertEquals(TimelockRecovery.QR_HEX_CHUNK_SIZE, parts.get(0).length());
        Assertions.assertEquals(1, parts.get(1).length());
        Assertions.assertEquals(0, TimelockRecovery.QR_HEX_CHUNK_SIZE % TimelockRecovery.QR_HEX_LINE_LENGTH);
    }

    @Test
    public void explorerUrlsMainnet() {
        Network.set(Network.MAINNET);
        Assertions.assertEquals("https://mempool.space/tx/push", TimelockRecovery.mempoolPushUrl());
        Assertions.assertEquals("https://blockstream.info/tx/push", TimelockRecovery.blockstreamPushUrl());
        Assertions.assertEquals("https://coinb.in/#broadcast", TimelockRecovery.coinbinBroadcastUrl());
        Assertions.assertEquals("https://mempool.space/tx/abcd", TimelockRecovery.mempoolTxUrl("abcd"));
        Assertions.assertEquals("https://blockstream.info/tx/abcd", TimelockRecovery.blockstreamTxUrl("abcd"));
    }

    @Test
    public void explorerUrlsTestnet() {
        Network.set(Network.TESTNET);
        Assertions.assertEquals("https://mempool.space/testnet/tx/push", TimelockRecovery.mempoolPushUrl());
        Assertions.assertEquals("https://blockstream.info/testnet/tx/abcd", TimelockRecovery.blockstreamTxUrl("abcd"));
        Network.set(Network.MAINNET);
    }

    @Test
    public void rejectsMineDestination() throws Exception {
        Wallet wallet = fundedWallet(ScriptType.P2WPKH, PolicyType.SINGLE_HD);
        Address ownAddress = wallet.getFreshNode(KeyPurpose.RECEIVE).getAddress();
        TimelockRecoveryException exception = Assertions.assertThrows(TimelockRecoveryException.class, () ->
                TimelockRecovery.create(wallet, List.of(TimelockRecoveryRecipient.remaining(ownAddress, null)),
                        90, 1.0, false, 850010));
        Assertions.assertTrue(exception.getMessage().contains("must not belong to this wallet"));
    }

    @Test
    public void rejectsInvalidDays() throws Exception {
        Wallet wallet = fundedWallet(ScriptType.P2WPKH, PolicyType.SINGLE_HD);
        Address destination = destination();
        Assertions.assertThrows(TimelockRecoveryException.class, () ->
                TimelockRecovery.create(wallet, List.of(TimelockRecoveryRecipient.remaining(destination, null)),
                        1, 1.0, false, 850010));
    }

    @Test
    public void frozenUtxoCount() throws Exception {
        Wallet wallet = fundedWallet(ScriptType.P2WPKH, PolicyType.SINGLE_HD);
        WalletNode receive0 = wallet.getNode(KeyPurpose.RECEIVE).getChildren().iterator().next();
        receive0.getUnspentTransactionOutputs().iterator().next().setStatus(Status.FROZEN);
        Assertions.assertEquals(1, TimelockRecovery.frozenUtxoCount(wallet));
    }

    @Test
    public void splitHexForQrChunksLargerPayloads() {
        Assertions.assertEquals(1, TimelockRecovery.splitHexForQr("C".repeat(TimelockRecovery.QR_HEX_CHUNK_SIZE)).size());
        List<String> twoChunks = TimelockRecovery.splitHexForQr("D".repeat(TimelockRecovery.QR_HEX_CHUNK_SIZE * 2));
        Assertions.assertEquals(2, twoChunks.size());
        Assertions.assertEquals(TimelockRecovery.QR_HEX_CHUNK_SIZE, twoChunks.get(0).length());
        Assertions.assertEquals(TimelockRecovery.QR_HEX_CHUNK_SIZE, twoChunks.get(1).length());
        List<String> threeParts = TimelockRecovery.splitHexForQr("E".repeat(TimelockRecovery.QR_HEX_CHUNK_SIZE * 2 + 1));
        Assertions.assertEquals(3, threeParts.size());
        Assertions.assertEquals(1, threeParts.get(2).length());
    }

    @Test
    public void rejectsEmptyRecipientsAndInvalidFee() throws Exception {
        Wallet wallet = fundedWallet(ScriptType.P2WPKH, PolicyType.SINGLE_HD);
        Address destination = destination();
        Assertions.assertThrows(TimelockRecoveryException.class, () ->
                TimelockRecovery.create(wallet, List.of(), 90, 1.0, false, 850010));
        Assertions.assertThrows(TimelockRecoveryException.class, () ->
                TimelockRecovery.create(wallet, List.of(TimelockRecoveryRecipient.remaining(destination, null)),
                        90, 0, false, 850010));
        Assertions.assertThrows(TimelockRecoveryException.class, () ->
                TimelockRecovery.create(wallet, List.of(TimelockRecoveryRecipient.of(destination, null, 599),
                                TimelockRecoveryRecipient.remaining(TimelockRecoveryFixtures.externalAddress(1), null)),
                        90, 1.0, false, 850010));
    }

    @Test
    public void rejectsWrongRemainingCount() throws Exception {
        Wallet wallet = fundedWallet(ScriptType.P2WPKH, PolicyType.SINGLE_HD);
        Address first = destination();
        Address second = TimelockRecoveryFixtures.externalAddress(1);
        Assertions.assertThrows(TimelockRecoveryException.class, () ->
                TimelockRecovery.create(wallet, List.of(TimelockRecoveryRecipient.of(first, null, 10_000)),
                        90, 1.0, false, 850010));
        Assertions.assertThrows(TimelockRecoveryException.class, () ->
                TimelockRecovery.create(wallet, List.of(
                        TimelockRecoveryRecipient.remaining(first, null),
                        TimelockRecoveryRecipient.remaining(second, null)),
                        90, 1.0, false, 850010));
    }

    @Test
    public void rejectsEmptyWallet() throws Exception {
        Wallet wallet = fundedWallet(ScriptType.P2WPKH, PolicyType.SINGLE_HD, 1, 1_000_000L);
        wallet.getNode(KeyPurpose.RECEIVE).getChildren().forEach(node -> node.getTransactionOutputs().clear());
        Assertions.assertThrows(TimelockRecoveryException.class, () ->
                TimelockRecovery.create(wallet, List.of(TimelockRecoveryRecipient.remaining(destination(), null)),
                        90, 1.0, false, 850010));
    }

    @Test
    public void reserveNodesReuseLabeledAddresses() throws Exception {
        Wallet wallet = fundedWallet(ScriptType.P2WPKH, PolicyType.SINGLE_HD);
        WalletNode initiation1 = TimelockRecovery.reserveInitiationNode(wallet);
        WalletNode initiation2 = TimelockRecovery.reserveInitiationNode(wallet);
        Assertions.assertEquals(initiation1.getIndex(), initiation2.getIndex());
        Assertions.assertEquals(TimelockRecovery.INITIATION_ADDRESS_LABEL, initiation2.getLabel());

        WalletNode cancellation = TimelockRecovery.reserveCancellationNode(wallet, initiation1);
        Assertions.assertNotEquals(initiation1.getIndex(), cancellation.getIndex());
        Assertions.assertEquals(TimelockRecovery.CANCELLATION_ADDRESS_LABEL, cancellation.getLabel());
        Assertions.assertEquals(cancellation.getIndex(), TimelockRecovery.reserveCancellationNode(wallet, initiation1).getIndex());
    }

    @Test
    public void reserveNodesSkipUserLabeledUnusedAddresses() throws Exception {
        Wallet wallet = fundedWallet(ScriptType.P2WPKH, PolicyType.SINGLE_HD);
        WalletNode userInitiation = wallet.getFreshNode(KeyPurpose.RECEIVE);
        userInitiation.setLabel("Keep my initiation note");
        WalletNode initiation = TimelockRecovery.reserveInitiationNode(wallet);
        Assertions.assertNotEquals(userInitiation.getIndex(), initiation.getIndex());
        Assertions.assertEquals("Keep my initiation note", userInitiation.getLabel());
        Assertions.assertEquals(TimelockRecovery.INITIATION_ADDRESS_LABEL, initiation.getLabel());

        WalletNode userCancellation = wallet.getFreshNode(KeyPurpose.RECEIVE, initiation);
        userCancellation.setLabel("Keep my cancellation note");
        WalletNode cancellation = TimelockRecovery.reserveCancellationNode(wallet, initiation);
        Assertions.assertNotEquals(userCancellation.getIndex(), cancellation.getIndex());
        Assertions.assertEquals("Keep my cancellation note", userCancellation.getLabel());
        Assertions.assertEquals(TimelockRecovery.CANCELLATION_ADDRESS_LABEL, cancellation.getLabel());
        Assertions.assertTrue(cancellation.getIndex() > initiation.getIndex());
    }

    @Test
    public void extractSignedRequiresSignatures() throws Exception {
        Wallet wallet = fundedWallet(ScriptType.P2WPKH, PolicyType.SINGLE_HD);
        TimelockRecovery recovery = TimelockRecovery.create(wallet,
                List.of(TimelockRecoveryRecipient.remaining(destination(), "Backup")),
                90, 1.0, true, 850010);
        TimelockRecoveryException unsigned = Assertions.assertThrows(TimelockRecoveryException.class, recovery::extractSignedInitiation);
        Assertions.assertTrue(unsigned.getMessage().contains("not fully signed"));
        Assertions.assertThrows(TimelockRecoveryException.class, () -> TimelockRecoveryPlan.from(recovery));
        Assertions.assertThrows(TimelockRecoveryException.class, () -> TimelockCancellationPlan.from(recovery));
    }

    @Test
    public void applySignedInitiationRejectsDifferentTransaction() throws Exception {
        Wallet wallet = fundedWallet(ScriptType.P2WPKH, PolicyType.SINGLE_HD);
        List<TimelockRecoveryRecipient> recipients = List.of(TimelockRecoveryRecipient.remaining(destination(), "Backup"));
        TimelockRecovery first = TimelockRecovery.create(wallet, recipients, 90, 1.0, false, 850010);
        TimelockRecovery second = TimelockRecovery.create(wallet, recipients, 90, 5.0, false, 850010);
        wallet.sign(second.getInitiationPsbt());
        Transaction signedSecond = second.extractSigned(second.getInitiationPsbt());
        Assertions.assertThrows(TimelockRecoveryException.class, () -> first.applySignedInitiation(signedSecond));
    }

    @Test
    public void p2wshMultisigCreateAndSign() throws Exception {
        Wallet wallet = fundedP2wshMultisig();
        Assertions.assertTrue(wallet.isValid());
        TimelockRecovery recovery = TimelockRecoveryFixtures.signedRecovery(wallet,
                List.of(TimelockRecoveryRecipient.remaining(destination(), "Backup")),
                90, 2.0, true);
        Assertions.assertTrue(recovery.getSignedInitiationTx().hasWitnesses());
        Assertions.assertEquals(1, recovery.getRecoveryPsbt().getTransaction().getInputs().size());
        Assertions.assertEquals(recovery.getInitiationTxid(), recovery.getSignedRecoveryTx().getInputs().get(0).getOutpoint().getHash());
        TimelockRecoveryPlan plan = TimelockRecoveryPlan.from(recovery);
        Assertions.assertEquals("Sparrow", plan.getFields().get("wallet_kind"));
        Assertions.assertEquals(TimelockRecovery.toUpperHex(recovery.getSignedInitiationTx()), plan.getFields().get("alert_tx"));
        Assertions.assertEquals(TimelockRecovery.toUpperHex(recovery.getSignedInitiationTx()),
                ((String)plan.getFields().get("alert_tx")).toUpperCase());
    }

    @Test
    public void cancellationPlanRequiresCancellation() throws Exception {
        Wallet wallet = fundedWallet(ScriptType.P2WPKH, PolicyType.SINGLE_HD);
        TimelockRecovery recovery = TimelockRecoveryFixtures.signedRecovery(wallet,
                List.of(TimelockRecoveryRecipient.remaining(destination(), "Backup")),
                90, 2.0, false);
        Assertions.assertFalse(recovery.hasCancellation());
        Assertions.assertThrows(TimelockRecoveryException.class, () -> TimelockCancellationPlan.from(recovery));
    }

    @Test
    public void bip128PlanContainsMandatoryFields() throws Exception {
        Wallet wallet = fundedWallet(ScriptType.P2WPKH, PolicyType.SINGLE_HD);
        TimelockRecovery recovery = TimelockRecoveryFixtures.signedRecovery(wallet,
                List.of(TimelockRecoveryRecipient.remaining(destination(), "Heir")),
                30, 2.0, false);
        TimelockRecoveryPlan plan = TimelockRecoveryPlan.from(recovery);
        Assertions.assertEquals("timelock-recovery-plan", plan.getFields().get("kind"));
        Assertions.assertEquals(30, plan.getFields().get("timelock_days"));
        Assertions.assertEquals(TimelockRecovery.ANCHOR_AMOUNT_SATS, ((Number)plan.getFields().get("anchor_amount_sats")).longValue());
        Assertions.assertEquals(recovery.getInitiationAddress().toString(), plan.getFields().get("alert_address"));
        Assertions.assertEquals(recovery.getSignedInitiationTx().getTxId().toString(), plan.getFields().get("alert_txid"));
        Assertions.assertEquals(recovery.getInitiationInputRefs(), plan.getFields().get("alert_inputs"));
        Assertions.assertTrue(((String)plan.getFields().get("alert_tx")).matches("[0-9A-F]+"));
        Assertions.assertEquals(8, plan.getChecksum().length());
    }
}
