package com.sparrowwallet.sparrow.timelockrecovery;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sparrowwallet.drongo.protocol.Transaction;
import com.sparrowwallet.drongo.protocol.TransactionOutput;
import com.sparrowwallet.drongo.wallet.Payment;
import com.sparrowwallet.sparrow.SparrowWallet;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class TimelockRecoveryPlan {
    public static final String KIND = "timelock-recovery-plan";
    public static final String WALLET_KIND = "Sparrow";
    public static final String WALLET_NAME = "Sparrow";

    private final Map<String, Object> fields;

    public TimelockRecoveryPlan(Map<String, Object> fields) {
        this.fields = new LinkedHashMap<>(fields);
    }

    public static TimelockRecoveryPlan from(TimelockRecovery recovery, Instant createdAt, String id) throws TimelockRecoveryException {
        Transaction initiationTx = requireTx(recovery.getSignedInitiationTx(), "Initiation");
        Transaction recoveryTx = requireTx(recovery.getSignedRecoveryTx(), "Recovery");

        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("kind", KIND);
        fields.put("id", id);
        fields.put("name", "Recovery Plan " + id);
        String description = "Recovery plan for wallet " + recovery.getWallet().getFullName();
        if(description.length() > 200) {
            description = description.substring(0, 200);
        }
        fields.put("description", description);
        fields.put("created_at", createdAt.toString());
        fields.put("plugin_version", SparrowWallet.APP_VERSION);
        fields.put("wallet_version", SparrowWallet.APP_VERSION);
        fields.put("wallet_name", WALLET_NAME);
        fields.put("wallet_kind", WALLET_KIND);
        fields.put("timelock_days", recovery.getTimelockDays());
        fields.put("anchor_amount_sats", TimelockRecovery.ANCHOR_AMOUNT_SATS);
        fields.put("anchor_addresses", recovery.getAnchorAddresses().stream().map(Object::toString).toList());
        fields.put("alert_address", recovery.getInitiationAddress().toString());
        fields.put("alert_inputs", recovery.getInitiationInputRefs());
        fields.put("alert_tx", TimelockRecovery.toUpperHex(initiationTx));
        fields.put("alert_txid", initiationTx.getTxId().toString());
        fields.put("alert_fee", recovery.getInitiationWalletTx().getFee());
        fields.put("alert_weight", initiationTx.getWeightUnits());
        fields.put("recovery_tx", TimelockRecovery.toUpperHex(recoveryTx));
        fields.put("recovery_txid", recoveryTx.getTxId().toString());
        fields.put("recovery_fee", recovery.getRecoveryWalletTx().getFee());
        fields.put("recovery_weight", recoveryTx.getWeightUnits());
        fields.put("recovery_outputs", recoveryOutputs(recoveryTx, recovery.getRecoveryWalletTx().getPayments()));
        return new TimelockRecoveryPlan(Bip128Json.withChecksum(fields));
    }

    public static TimelockRecoveryPlan from(TimelockRecovery recovery) throws TimelockRecoveryException {
        return from(recovery, Instant.now(), UUID.randomUUID().toString());
    }

    private static Transaction requireTx(Transaction transaction, String name) throws TimelockRecoveryException {
        if(transaction == null) {
            throw new TimelockRecoveryException(name + " transaction is not fully signed");
        }
        return transaction;
    }

    private static List<List<Object>> recoveryOutputs(Transaction recoveryTx, List<Payment> payments) {
        List<List<Object>> outputs = new ArrayList<>();
        for(int i = 0; i < recoveryTx.getOutputs().size(); i++) {
            TransactionOutput output = recoveryTx.getOutputs().get(i);
            List<Object> tuple = new ArrayList<>();
            tuple.add(output.getScript().getToAddress().toString());
            tuple.add(output.getValue());
            if(i < payments.size() && payments.get(i).getLabel() != null && !payments.get(i).getLabel().isBlank()) {
                String label = payments.get(i).getLabel();
                tuple.add(label.length() > 200 ? label.substring(0, 200) : label);
            }
            outputs.add(tuple);
        }
        return outputs;
    }

    public Map<String, Object> getFields() {
        return Collections.unmodifiableMap(fields);
    }

    public String getChecksum() {
        return (String)fields.get("checksum");
    }

    public String toPrettyJson() {
        Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
        return gson.toJson(fields);
    }
}
