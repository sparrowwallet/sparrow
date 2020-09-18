package com.sparrowwallet.sparrow.net;

import com.google.gson.Gson;
import com.sparrowwallet.drongo.address.Address;
import com.sparrowwallet.drongo.address.InvalidAddressException;
import com.sparrowwallet.drongo.crypto.ECKey;
import com.sparrowwallet.drongo.protocol.ScriptType;
import com.sparrowwallet.sparrow.MainApp;
import com.sparrowwallet.sparrow.event.VersionUpdatedEvent;
import javafx.concurrent.ScheduledService;
import javafx.concurrent.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.HttpsURLConnection;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.security.SignatureException;
import java.util.Map;

public class VersionCheckService extends ScheduledService<VersionUpdatedEvent> {
    private static final Logger log = LoggerFactory.getLogger(VersionCheckService.class);
    private static final String VERSION_CHECK_URL = "https://www.sparrowwallet.com/version";

    @Override
    protected Task<VersionUpdatedEvent> createTask() {
        return new Task<>() {
            protected VersionUpdatedEvent call() {
                try {
                    VersionCheck versionCheck = getVersionCheck();
                    if(isNewer(versionCheck) && verifySignature(versionCheck)) {
                        return new VersionUpdatedEvent(versionCheck.version);
                    }
                } catch(IOException e) {
                    log.error("Error retrieving version check file", e);
                }

                return null;
            }
        };
    }

    private VersionCheck getVersionCheck() throws IOException {
        URL url = new URL(VERSION_CHECK_URL);
        HttpsURLConnection conn = (HttpsURLConnection)url.openConnection();

        try(InputStreamReader reader = new InputStreamReader(conn.getInputStream())) {
            Gson gson = new Gson();
            return gson.fromJson(reader, VersionCheck.class);
        }
    }

    private boolean verifySignature(VersionCheck versionCheck) {
        try {
            for(String addressString : versionCheck.signatures.keySet()) {
                String signature = versionCheck.signatures.get(addressString);
                ECKey signedMessageKey = ECKey.signedMessageToKey(versionCheck.version, signature, false);
                Address providedAddress = Address.fromString(addressString);
                Address signedMessageAddress = ScriptType.P2PKH.getAddress(signedMessageKey);

                if(providedAddress.equals(signedMessageAddress)) {
                    return true;
                } else {
                    log.warn("Invalid signature for version check " + signature + " from address " + addressString);
                }
            }
        } catch(SignatureException e) {
            log.error("Error in version check signature", e);
        } catch(InvalidAddressException e) {
            log.error("Error in version check address", e);
        }

        return false;
    }

    private boolean isNewer(VersionCheck versionCheck) {
        try {
            Version versionCheckVersion = new Version(versionCheck.version);
            Version currentVersion = new Version(MainApp.APP_VERSION);
            return versionCheckVersion.compareTo(currentVersion) > 0;
        } catch(IllegalArgumentException e) {
            log.error("Invalid versions to compare: " + versionCheck.version + " to " + MainApp.APP_VERSION, e);
        }

        return false;
    }

    private static class VersionCheck {
        public String version;
        public Map<String, String> signatures;
    }

    public static class Version implements Comparable<Version> {
        private final String version;

        public final String get() {
            return this.version;
        }

        public Version(String version) {
            if(version == null) {
                throw new IllegalArgumentException("Version can not be null");
            }
            if(!version.matches("[0-9]+(\\.[0-9]+)*")) {
                throw new IllegalArgumentException("Invalid version format");
            }
            this.version = version;
        }

        @Override
        public int compareTo(Version that) {
            if(that == null) {
                return 1;
            }
            String[] thisParts = this.get().split("\\.");
            String[] thatParts = that.get().split("\\.");
            int length = Math.max(thisParts.length, thatParts.length);
            for(int i = 0; i < length; i++) {
                int thisPart = i < thisParts.length ? Integer.parseInt(thisParts[i]) : 0;
                int thatPart = i < thatParts.length ? Integer.parseInt(thatParts[i]) : 0;
                if(thisPart < thatPart) {
                    return -1;
                }
                if(thisPart > thatPart) {
                    return 1;
                }
            }
            return 0;
        }

        @Override
        public boolean equals(Object that) {
            if(this == that) {
                return true;
            }
            if(that == null) {
                return false;
            }
            if(this.getClass() != that.getClass()) {
                return false;
            }
            return this.compareTo((Version)that) == 0;
        }
    }
}
