package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.sparrow.Theme;

public class ThemeChangedEvent {
    private final Theme theme;

    public ThemeChangedEvent(Theme theme) {
        this.theme = theme;
    }

    public Theme getTheme() {
        return theme;
    }
}
