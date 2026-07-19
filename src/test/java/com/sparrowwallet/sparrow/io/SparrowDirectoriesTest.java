package com.sparrowwallet.sparrow.io;

import com.sparrowwallet.drongo.OsType;
import com.sparrowwallet.sparrow.SparrowWallet;
import com.sparrowwallet.sparrow.io.Storage.SparrowDirectories;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

@Execution(ExecutionMode.SAME_THREAD)
class SparrowDirectoriesTests {
    private static final String USER_HOME = "user.home";

    private Map<String, String> propsToRestore = Map.of();

    @TempDir File xdgConfig;
    @TempDir File xdgData;
    @TempDir File xdgState;
    @TempDir File xdgCache;

    @TempDir File userHome;
    @TempDir File appHome;

    private Map<String, String> getValidXdgEnvVarMap() {
        Map<String, String> envVars = new HashMap<>();
        envVars.put(SparrowDirectories.XDG_CONFIG_HOME, xdgConfig.getAbsolutePath());
        envVars.put(SparrowDirectories.XDG_DATA_HOME, xdgData.getAbsolutePath());
        envVars.put(SparrowDirectories.XDG_STATE_HOME, xdgState.getAbsolutePath());
        envVars.put(SparrowDirectories.XDG_CACHE_HOME, xdgCache.getAbsolutePath());
        return envVars;
    }

    private Map<String, String> getClearedXdgEnvVarMap() {
        Map<String, String> envVars = new HashMap<>();
        envVars.put(SparrowDirectories.XDG_CONFIG_HOME, null);
        envVars.put(SparrowDirectories.XDG_DATA_HOME, null);
        envVars.put(SparrowDirectories.XDG_STATE_HOME, null);
        envVars.put(SparrowDirectories.XDG_CACHE_HOME, null);
        return envVars;
    }

    private Map<String, String> getValidAppHomePropsMap () {
        Map<String, String> props = new HashMap<>();
        props.put(SparrowWallet.APP_HOME_PROPERTY, appHome.getAbsolutePath());
        return props;
    }

    private void preparePropsMock(Map<String, String> props) {
        props.forEach(this::overrideProp);
    }

    private void overrideProp(String key, String value) {
        // only keep original value, ignore consecutive changes
        if(!propsToRestore.containsKey(key)) {
            propsToRestore.put(key, System.getProperty(key));
        }

        if(value != null) {
            System.setProperty(key, value);
        } else {
            System.clearProperty(key);
        }
    }

    private void prepareEnvVarMock(Map<String, String> envVarOverrides) {
        Storage.envRetriever = (String key) -> {
            if(!envVarOverrides.containsKey(key)) {
                return System.getenv(key);
            } else {
                return envVarOverrides.get(key);
            }
        };
    }

    private void prepareMocks(
            OsType osType,
            Map<String, String> envVarOverrides,
            Map<String, String> propOverrides
        ) {
        Storage.osTypeSupplier = () -> osType;

        prepareEnvVarMock(envVarOverrides);

        propOverrides.forEach(this::overrideProp);
    }

    @BeforeEach
    void setupCases() {
        propsToRestore = new HashMap<>();
    }

    private void testDirectories(boolean defaultFlag, SparrowDirectories expectedDirs) {
        SparrowDirectories sparrowHomeDirs = SparrowDirectories.getHomeDirs(defaultFlag);

        Assertions.assertEquals(expectedDirs, sparrowHomeDirs);
    }

    private void testCanUseXdgDirs(boolean expected) {
        boolean actual = SparrowDirectories.canUseXdgDirs();
        Assertions.assertEquals(expected, actual, String.format("Expected canUseXdgDirs() to return [%b] but was [%b]", expected, actual));
    }

    @AfterEach
    void tearDownCases() {
        Storage.envRetriever = System::getenv;
        Storage.osTypeSupplier = OsType::getCurrent;

        for(Map.Entry<String, String> entry : propsToRestore.entrySet()) {
            String key = entry.getKey();
            String originalValue = propsToRestore.get(key);

            if(originalValue != null) {
                System.setProperty(key, originalValue);
            } else {
                System.clearProperty(key);
            }
        }
    }

    @Nested
    class WindowsTests {
        final OsType osType = OsType.WINDOWS;

        @TempDir
        File appData;

        @BeforeEach
        private void setup() {
            Map<String, String> envVars = new HashMap<>();
            envVars.put(Storage.ENV_APPDATA, appData.getAbsolutePath());

            Map<String, String> props = new HashMap<>();
            props.put(SparrowWallet.APP_HOME_PROPERTY, null);
            props.putAll(getValidXdgEnvVarMap());

            File xdgSparrowConfig = new File(xdgConfig, SparrowDirectories.XDG_SPARROW_DIR);
            xdgSparrowConfig.mkdirs();

            prepareMocks(osType, envVars, props);
        }

        @Test
        void shouldUseAppDataDir() {
            boolean defaultFlag = false;

            File expectedLocation = new File(appData, Storage.WINDOWS_SPARROW_DIR);
            SparrowDirectories expected = SparrowDirectories.fromSingleDir(expectedLocation);
            testDirectories(defaultFlag, expected);

            testCanUseXdgDirs(false);
        }

        @Test
        void shouldUseAppHomeIfSet() {
            preparePropsMock(getValidAppHomePropsMap());
            boolean defaultFlag = false;

            SparrowDirectories expected = SparrowDirectories.fromSingleDir(appHome);
            testDirectories(defaultFlag, expected);

            testCanUseXdgDirs(false);
        }

        @Test
        void defaultFlagHasPrioOverAppHome() {
            preparePropsMock(getValidAppHomePropsMap());
            boolean defaultFlag = true;

            File expectedLocation = new File(appData, Storage.WINDOWS_SPARROW_DIR);
            SparrowDirectories expected = SparrowDirectories.fromSingleDir(expectedLocation);
            testDirectories(defaultFlag, expected);

            testCanUseXdgDirs(false);
        }
    }

    @Nested
    class UnixTests {
        OsType getOsType() {
            return OsType.UNIX;
        }

        private enum XdgBaseLocation {
            DEFAULT,
            ENV_VAR
        }

        private void createSparrowXdgConfigFolder(XdgBaseLocation location) {
            File sparrowXdgConfig;
            if (location == XdgBaseLocation.ENV_VAR) {
                sparrowXdgConfig = new File(xdgConfig, SparrowDirectories.XDG_SPARROW_DIR);
            } else {
                sparrowXdgConfig = userHome.toPath()
                    .resolve(".config")
                    .resolve(SparrowDirectories.XDG_SPARROW_DIR)
                    .toFile();
            }

            sparrowXdgConfig.mkdirs();
        }

        @BeforeEach
        private void setup() {
            Map<String, String> props = new HashMap<>();
            props.put(USER_HOME, userHome.getAbsolutePath());
            props.put(SparrowWallet.APP_HOME_PROPERTY, null);

            prepareMocks(getOsType(), getValidXdgEnvVarMap(), props);
        }

        @Test
        void shouldUseUserHomeIfNoXdgFolderExists() {
            boolean defaultFlag = false;

            File expectedLocation = new File(userHome, Storage.SPARROW_DIR);
            SparrowDirectories expected = SparrowDirectories.fromSingleDir(expectedLocation);
            testDirectories(defaultFlag, expected);

            testCanUseXdgDirs(false);
        }

        @Test
        void shouldUseXdgIfFolderExists() {
            createSparrowXdgConfigFolder(XdgBaseLocation.ENV_VAR);

            boolean defaultFlag = false;

            SparrowDirectories baseDirs = new SparrowDirectories(
                xdgConfig,
                xdgData,
                xdgCache,
                xdgState
            );
            SparrowDirectories expected = SparrowDirectories.append(baseDirs, SparrowDirectories.XDG_SPARROW_DIR);

            testDirectories(defaultFlag, expected);

            testCanUseXdgDirs(true);
        }

        @Test
        void shouldUseXdgWithoutEnvVarsIfDefaultLocationExists() {
            prepareEnvVarMock(getClearedXdgEnvVarMap());
            createSparrowXdgConfigFolder(XdgBaseLocation.DEFAULT);


            boolean defaultFlag = false;

            SparrowDirectories baseDirs = new SparrowDirectories(
                userHome.toPath().resolve(".config").toFile(),
                userHome.toPath().resolve(".local/share").toFile(),
                userHome.toPath().resolve(".cache").toFile(),
                userHome.toPath().resolve(".local/state").toFile()
            );
            SparrowDirectories expected = SparrowDirectories.append(baseDirs, SparrowDirectories.XDG_SPARROW_DIR);
            testDirectories(defaultFlag, expected);

            testCanUseXdgDirs(true);
        }

        @Test
        void appHomeHasPrioOverXdg() {
            preparePropsMock(getValidAppHomePropsMap());
            createSparrowXdgConfigFolder(XdgBaseLocation.ENV_VAR);

            boolean defaultFlag = false;

            SparrowDirectories expected = SparrowDirectories.fromSingleDir(appHome);
            testDirectories(defaultFlag, expected);

            testCanUseXdgDirs(true);
        }

        @Test
        void defaultFlagHasPrioOverAppHome() {
            preparePropsMock(getValidAppHomePropsMap());
            createSparrowXdgConfigFolder(XdgBaseLocation.ENV_VAR);

            boolean defaultFlag = true;

            File expectedLocation = new File(userHome, Storage.SPARROW_DIR);
            SparrowDirectories expected = SparrowDirectories.fromSingleDir(expectedLocation);
            testDirectories(defaultFlag, expected);

            testCanUseXdgDirs(true);
        }
    }
}
