package com.sparrowwallet.sparrow.timelockrecovery;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sparrowwallet.drongo.protocol.Transaction;
import com.sparrowwallet.sparrow.SparrowWallet;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class TimelockCancellationPlan {
    public static final String KIND = "timelock-cancellation-plan";

    private final Map<String, Object> fields;

    public TimelockCancellationPlan(Map<String, Object> fields) {
        this.fields = new LinkedHashMap<>(fields);
    }

    public static TimelockCancellationPlan from(TimelockRecovery recovery, Instant createdAt, String id) throws TimelockRecoveryException {
        if(!recovery.hasCancellation()) {
            throw new TimelockRecoveryException("No cancellation transaction was created");
        }
        Transaction initiationTx = recovery.getSignedInitiationTx();
        Transaction cancellationTx = recovery.getSignedCancellationTx();
        if(initiationTx == null || cancellationTx == null) {
            throw new TimelockRecoveryException("Initiation and Cancellation transactions must be fully signed");
        }

        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("kind", KIND);
        fields.put("id", id);
        fields.put("created_at", createdAt.toString());
        fields.put("plugin_version", SparrowWallet.APP_VERSION);
        fields.put("wallet_kind", TimelockRecoveryPlan.WALLET_KIND);
        fields.put("wallet_version", SparrowWallet.APP_VERSION);
        fields.put("wallet_name", TimelockRecoveryPlan.WALLET_NAME);
        fields.put("timelock_days", recovery.getTimelockDays());
        fields.put("alert_txid", initiationTx.getTxId().toString());
        fields.put("cancellation_address", recovery.getCancellationAddress().toString());
        fields.put("cancellation_tx", TimelockRecovery.toUpperHex(cancellationTx));
        fields.put("cancellation_txid", cancellationTx.getTxId().toString());
        fields.put("cancellation_fee", recovery.getCancellationWalletTx().getFee());
        fields.put("cancellation_weight", cancellationTx.getWeightUnits());
        fields.put("cancellation_amount", cancellationTx.getOutputs().get(0).getValue());
        return new TimelockCancellationPlan(Bip128Json.withChecksum(fields));
    }

    public static TimelockCancellationPlan from(TimelockRecovery recovery) throws TimelockRecoveryException {
        return from(recovery, Instant.now(), UUID.randomUUID().toString());
    }

    public Map<String, Object> getFields() {
        return java.util.Collections.unmodifiableMap(fields);
    }

    public String getChecksum() {
        return (String)fields.get("checksum");
    }

    public String toPrettyJson() {
        Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
        return gson.toJson(fields);
    }
}
