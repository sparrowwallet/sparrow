package com.sparrowwallet.sparrow.i18n;

import java.util.*;

public class LanguagesManager {

    public static final Language DEFAULT_LANGUAGE = Language.ENGLISH;

    private static ResourceBundle bundle;

    public static ResourceBundle getResourceBundle() {
        if(bundle == null) {
            loadLanguage(DEFAULT_LANGUAGE.getCode());
        }
        return bundle;
    }

    public static void loadLanguage(String language) {
        Locale locale = new Locale(language);

        if(bundle == null || !locale.equals(bundle.getLocale())) {
            bundle = ResourceBundle.getBundle("i18n/messages", locale);
        }
    }

    public static String getMessage(String key) { return bundle.getString(key); }
}