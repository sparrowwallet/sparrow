package com.sparrowwallet.sparrow;

import java.io.File;

public class TabData {
    private TabType type;
    private File file;
    private String text;

    public TabData(TabType type) {
        this.type = type;
    }

    public TabType getType() {
        return type;
    }

    public File getFile() {
        return file;
    }

    public void setFile(File file) {
        this.file = file;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public enum TabType {
        WALLET, TRANSACTION
    }
}
