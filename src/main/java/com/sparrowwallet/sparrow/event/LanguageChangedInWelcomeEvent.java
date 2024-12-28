package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.sparrow.i18n.Language;

public class LanguageChangedInWelcomeEvent {
    private final Language language;

    public LanguageChangedInWelcomeEvent(Language language) {
        this.language = language;
    }

    public Language getLanguage() {
        return language;
    }
}
