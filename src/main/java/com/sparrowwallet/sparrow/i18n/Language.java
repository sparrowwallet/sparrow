package com.sparrowwallet.sparrow.i18n;

public enum Language {
    ENGLISH("en"),
    SPANISH("es");

    private final String code;

    Language(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public static Language getFromCode(String code) {
        for (Language l : Language.values()) {
            if (l.code.equals(code)) {
                return l;
            }
        }
        return null;
    }
}