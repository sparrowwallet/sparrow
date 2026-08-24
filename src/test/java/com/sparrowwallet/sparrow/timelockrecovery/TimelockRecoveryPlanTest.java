package com.sparrowwallet.sparrow.timelockrecovery;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TimelockRecoveryPlanTest {
    @Test
    public void checksumBipExample() {
        Map<String, Object> jsonData = bipExampleWithoutChecksum();
        Assertions.assertEquals("92f8b3da", Bip128Json.checksum(jsonData));

        Map<String, Object> withChecksum = Bip128Json.withChecksum(jsonData);
        Assertions.assertEquals("92f8b3da", withChecksum.get("checksum"));
        Assertions.assertEquals("92f8b3da", Bip128Json.checksum(withChecksum));
    }

    @Test
    public void checksumNonAscii() {
        Map<String, Object> jsonData = new LinkedHashMap<>();
        jsonData.put("wallet_name", "Ωmega Wörld Ñoño 日本語 中文 עברית العربية");
        jsonData.put("id", "abc-123");
        Assertions.assertEquals("74674eca", Bip128Json.checksum(jsonData));
    }

    @Test
    public void omitsNullAndChecksumFields() {
        Map<String, Object> jsonData = new LinkedHashMap<>();
        jsonData.put("id", "abc-123");
        jsonData.put("wallet_name", "Ωmega Wörld Ñoño 日本語 中文 עברית العربية");
        jsonData.put("description", null);
        jsonData.put("checksum", "deadbeef");
        Assertions.assertEquals("74674eca", Bip128Json.checksum(jsonData));
    }

    @Test
    public void prettyJsonRoundTripContainsMandatoryFields() {
        Map<String, Object> fields = Bip128Json.withChecksum(bipExampleWithoutChecksum());
        TimelockRecoveryPlan plan = new TimelockRecoveryPlan(fields);
        String json = plan.toPrettyJson();
        Assertions.assertTrue(json.contains("\"kind\": \"timelock-recovery-plan\""));
        Assertions.assertTrue(json.contains("\"checksum\": \"92f8b3da\""));
        Assertions.assertTrue(json.contains("0200000000010204F18C35EDB894"));
    }

    private static Map<String, Object> bipExampleWithoutChecksum() {
        Map<String, Object> jsonData = new LinkedHashMap<>();
        jsonData.put("kind", "timelock-recovery-plan");
        jsonData.put("id", "exported-692452189b301b561ed57cbe");
        jsonData.put("name", "Recovery Plan ac300e72-7612-497e-96b0-df2fdeda59ea");
        jsonData.put("description", "RITREK APP 1.1.0: Trezor Account #1");
        jsonData.put("created_at", "2025-11-24T12:39:53.532Z");
        jsonData.put("plugin_version", "1.0.1");
        jsonData.put("wallet_version", "1.0.1");
        jsonData.put("wallet_name", "RITREK Service");
        jsonData.put("wallet_kind", "RITREK BACKEND");
        jsonData.put("timelock_days", 2);
        jsonData.put("anchor_amount_sats", 600);
        jsonData.put("anchor_addresses", List.of("bc1qnda6x2gxdh3yujd2zjpsd7qzx3awxmlaf9wwlk"));
        jsonData.put("alert_address", "bc1qj0f9sjenwyjs0u7mlgvptjp05z3syzq7mru3ep");
        jsonData.put("alert_inputs", List.of(
                "a265a485df4c6417019b91379257eb387bceeda96f7bb6311794b8ed358cf104:0",
                "2f621c2151f33173983133cbc1000e3b603b8a18423b0379feffe8513171d5d3:0"));
        jsonData.put("alert_tx", "0200000000010204F18C35EDB8941731B67B6FA9EDCE7B38EB579237919B0117644CDF85A465A20000000000FDFFFFFFD3D5713151E8FFFE79033B42188A3B603B0E00C1CB3331987331F351211C622F0000000000FDFFFFFF0258020000000000001600149B7BA329066DE24E49AA148306F802347AE36FFD205600000000000016001493D2584B33712507F3DBFA1815C82FA0A302081E02483045022100DCDBAE77C35EB4A0B3ED0DE5484206AB6B07041BE99B2BBAF0243C125916523C0220396959C3C52B2B1F9E472AEEE7C5D9540531B131C3221DE942754C6D0941397D012103C08FF3ADBA14B742646572BCA6F07AEB910666FB28E4DDDC40E33755E7C869D30248304502210089084472FDA3CF82D6ABC11BF1A5E77C9B423617C8B840F58C02746035B3BA6302203942AA1FA13F952F49FB114D48130A9AAF70151E7D09036D15734DB1F41A8B6001210397064EDED7DAD7D662290DC2847E87C5C27DA8865B89DDB58FDE9A006BA7DB3900000000");
        jsonData.put("alert_txid", "f1413fedadaf30697820bcd8f6a393fcc73ea00a15bea3253f89d5658690d2f7");
        jsonData.put("alert_fee", 231);
        jsonData.put("alert_weight", 834);
        jsonData.put("recovery_tx", "02000000000101F7D2908665D5893F25A3BE150AA03EC7FC93A3F6D8BC20786930AFADED3F41F101000000005201400001A6550000000000001600149B7BA329066DE24E49AA148306F802347AE36FFD0247304402204AFF87C2127F5697F300C6522067A8D5E5290CA8D140D2E5BCEF4A36606C5FE5022056673BEC5BB459DFFBD4D266EE95AEF0D701383ED80BD433A02C3C486A826D76012102774DBCD59F2D08EFF718BC09972ADC609FBC31C26B551B3E4EA30A1D43EEDB9700000000");
        jsonData.put("recovery_txid", "bc304610e8f282036345e87163d4cba5b16488a3bf2e4d738379d7bda3a0bca3");
        jsonData.put("recovery_fee", 122);
        jsonData.put("recovery_weight", 437);
        jsonData.put("recovery_outputs", List.of(List.of("bc1qnda6x2gxdh3yujd2zjpsd7qzx3awxmlaf9wwlk", 21926L, "My Backup Wallet")));
        jsonData.put("metadata", "sig:825d6b3858c175c7fc16da3134030e095c4f9089c3c89722247eeedc08a7ef4f");
        return jsonData;
    }
}
