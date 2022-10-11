package com.sparrowwallet.sparrow.net;

import com.google.gson.Gson;
import com.sparrowwallet.drongo.address.Address;
import com.sparrowwallet.drongo.address.InvalidAddressException;
import com.sparrowwallet.drongo.crypto.ECKey;
import com.sparrowwallet.drongo.protocol.ScriptType;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.SparrowWallet;
import com.sparrowwallet.sparrow.event.VersionUpdatedEvent;
import javafx.concurrent.ScheduledService;
import javafx.concurrent.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.HttpsURLConnection;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Proxy;
import java.net.URL;
import java.nio.charset.StandardCharsets;
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
        Proxy proxy = AppServices.getProxy();

        if(log.isInfoEnabled()) {
            log.info("Requesting application version check from " + url);
        }

        HttpsURLConnection conn = (HttpsURLConnection)(proxy == null ? url.openConnection() : url.openConnection(proxy));

        try(InputStreamReader reader = new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8)) {
            Gson gson = new Gson();
            return gson.fromJson(reader, VersionCheck.class);
        }
    }

    private boolean verifySignature(VersionCheck versionCheck) {
        try {
            for(String addressString : versionCheck.signatures.keySet()) {
                if(!addressString.equals("1LiJx1HQ49L2LzhBwbgwXdHiGodvPg5YaV")) {
                    log.warn("Invalid address for version check " + addressString);
                    continue;
                }

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
            Version currentVersion = new Version(SparrowWallet.APP_VERSION);
            return versionCheckVersion.compareTo(currentVersion) > 0;
        } catch(IllegalArgumentException e) {
            log.error("Invalid versions to compare: " + versionCheck.version + " to " + SparrowWallet.APP_VERSION, e);
        }

        return false;
    }

    private static class VersionCheck {
        public String version;
        public Map<String, String> signatures;
    }

}
