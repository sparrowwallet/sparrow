package com.sparrowwallet.sparrow.io.db;

import com.sparrowwallet.drongo.crypto.ChallengeResponseProvider;
import com.sparrowwallet.drongo.crypto.ECKey;
import com.sparrowwallet.drongo.crypto.KeyCrypterException;
import com.sparrowwallet.sparrow.io.Storage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Arrays;

public class DbPersistenceChallengeResponseTest {
    private static ChallengeResponseProvider fixedResponse(byte fill) {
        byte[] response = new byte[20];
        Arrays.fill(response, fill);
        return challenge -> Arrays.copyOf(response, response.length);
    }

    @AfterEach
    public void clearFactory() {
        Storage.setChallengeResponseProviderFactory(null);
    }

    @Test
    public void anEmptyPasswordOnAChallengeResponseWalletStillDerivesAKey() throws IOException {
        Storage.setChallengeResponseProviderFactory(() -> fixedResponse((byte)0x5a));
        DbPersistence persistence = new DbPersistence();
        persistence.setChallengeResponseEnabled(true);

        ECKey key = persistence.getEncryptionKey("");

        //Returning the no password key here would open the wallet without the device
        Assertions.assertNotEquals(Storage.NO_PASSWORD_KEY, key);
    }

    @Test
    public void anEmptyPasswordWithoutChallengeResponseIsStillTheNoPasswordKey() throws IOException {
        DbPersistence persistence = new DbPersistence();

        Assertions.assertEquals(Storage.NO_PASSWORD_KEY, persistence.getEncryptionKey(""));
    }

    @Test
    public void derivationFailsRatherThanSkippingTheDeviceWhenNoProviderIsRegistered() {
        DbPersistence persistence = new DbPersistence();
        persistence.setChallengeResponseEnabled(true);

        Assertions.assertThrows(KeyCrypterException.class, () -> persistence.getEncryptionKey("password"));
    }

    @Test
    public void theResponseChangesTheDerivedKey() throws IOException {
        DbPersistence withoutDevice = new DbPersistence();
        ECKey plain = withoutDevice.getEncryptionKey("password");

        Storage.setChallengeResponseProviderFactory(() -> fixedResponse((byte)0x11));
        DbPersistence withDevice = new DbPersistence();
        withDevice.setKeyDeriver(withoutDevice.getKeyDeriver());
        withDevice.setChallengeResponseEnabled(true);

        Assertions.assertNotEquals(plain, withDevice.getEncryptionKey("password"));
    }

    @Test
    public void aDifferentResponseGivesADifferentKey() throws IOException {
        DbPersistence first = new DbPersistence();
        Storage.setChallengeResponseProviderFactory(() -> fixedResponse((byte)0x01));
        first.setChallengeResponseEnabled(true);
        ECKey firstKey = first.getEncryptionKey("password");

        DbPersistence second = new DbPersistence();
        second.setKeyDeriver(first.getKeyDeriver());
        Storage.setChallengeResponseProviderFactory(() -> fixedResponse((byte)0x02));
        second.setChallengeResponseEnabled(true);

        Assertions.assertNotEquals(firstKey, second.getEncryptionKey("password"));
    }

    @Test
    public void aFreshProviderIsUsedForEachDerivation() throws IOException {
        int[] created = {0};
        Storage.setChallengeResponseProviderFactory(() -> {
            created[0]++;
            return fixedResponse((byte)0x33);
        });
        DbPersistence persistence = new DbPersistence();
        persistence.setChallengeResponseEnabled(true);

        ECKey first = persistence.getEncryptionKey("password");
        ECKey second = persistence.getEncryptionKey("password");

        //A provider consumed once would leave the retry deriving without the device
        Assertions.assertEquals(2, created[0]);
        Assertions.assertEquals(first, second);
    }
}
