package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.drongo.crypto.ECKey;
import com.sparrowwallet.drongo.protocol.ScriptType;

public class DeviceGetPrivateKeyEvent {
    private final ECKey privateKey;
    private final ScriptType scriptType;

    public DeviceGetPrivateKeyEvent(ECKey privateKey, ScriptType scriptType) {
        this.privateKey = privateKey;
        this.scriptType = scriptType;
    }

    public ECKey getPrivateKey() {
        return privateKey;
    }

    public ScriptType getScriptType() {
        return scriptType;
    }
}
