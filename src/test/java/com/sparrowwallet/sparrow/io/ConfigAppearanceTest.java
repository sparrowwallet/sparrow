package com.sparrowwallet.sparrow.io;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.sparrowwallet.sparrow.Appearance;
import com.sparrowwallet.sparrow.Mode;
import com.sparrowwallet.sparrow.Theme;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigAppearanceTest {
    @Test
    void oldProfilesDefaultToClassic() throws ReflectiveOperationException {
        Config config = configGson().fromJson("""
                {"mode":"OFFLINE","theme":"SYSTEM","hideAmounts":true}
                """, Config.class);

        assertOfflinePrivateProfile(config, Appearance.CLASSIC);
    }

    @Test
    void unfamiliarAppearanceValuesLeaveOtherSettingsIntact() throws ReflectiveOperationException {
        Gson gson = configGson();
        for(String value : List.of("\"FUTURE\"", "null", "true", "12", "{}", "[\"MODERN\"]")) {
            Config config = gson.fromJson("""
                    {"mode":"OFFLINE","theme":"SYSTEM","hideAmounts":true,"appearance":%s}
                    """.formatted(value), Config.class);

            assertOfflinePrivateProfile(config, Appearance.CLASSIC);
        }
    }

    @Test
    void supportedValuesRoundTripIndependentlyOfTheme() throws ReflectiveOperationException {
        Gson gson = configGson();
        for(Appearance appearance : Appearance.values()) {
            for(Theme theme : Theme.values()) {
                Config config = gson.fromJson("""
                        {"mode":"OFFLINE","theme":"%s","hideAmounts":true,"appearance":"%s"}
                        """.formatted(theme.name(), appearance.name()), Config.class);

                JsonObject saved = gson.toJsonTree(config).getAsJsonObject();
                assertEquals(appearance.name(), saved.get("appearance").getAsString());
                assertEquals(theme.name(), saved.get("theme").getAsString());
                Config loaded = gson.fromJson(saved, Config.class);
                assertEquals(appearance, loaded.getAppearance());
                assertEquals(theme, loaded.getTheme());
                assertEquals(Mode.OFFLINE, loaded.getMode());
                assertTrue(loaded.isHideAmounts());
            }
        }
    }

    private static void assertOfflinePrivateProfile(Config config, Appearance appearance) {
        assertEquals(appearance, config.getAppearance());
        assertEquals(Mode.OFFLINE, config.getMode());
        assertEquals(Theme.SYSTEM, config.getTheme());
        assertTrue(config.isHideAmounts());
    }

    private static Gson configGson() throws ReflectiveOperationException {
        Method method = Config.class.getDeclaredMethod("getGson");
        method.setAccessible(true);
        return (Gson)method.invoke(null);
    }
}
