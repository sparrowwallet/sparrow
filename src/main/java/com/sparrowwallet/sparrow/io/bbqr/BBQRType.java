package com.sparrowwallet.sparrow.io.bbqr;

public enum BBQRType {
    PSBT("P"), TXN("T"), JSON("J"), CBOR("C"), UNICODE("U"), BINARY("B"), EXECUTABLE("X");

    private final String code;

    BBQRType(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public static BBQRType fromString(String code) {
        for(BBQRType type : values()) {
            if(type.getCode().equals(code)) {
                return type;
            }
        }

        throw new IllegalArgumentException("Could not find type for code " + code);
    }
}
