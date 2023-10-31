package com.sparrowwallet.sparrow.io.satochip;

import com.sparrowwallet.drongo.crypto.AESKeyCrypter;

import com.sparrowwallet.drongo.bip47.SecretPoint;
import com.sparrowwallet.drongo.crypto.ECKey;
import com.sparrowwallet.drongo.crypto.Key;
import com.sparrowwallet.drongo.crypto.EncryptedData;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.smartcardio.CardException;
import java.security.SecureRandom;
import java.nio.ByteBuffer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles a SecureChannel session with the card.
 */
public class SecureChannelSession {
    private static final Logger log = LoggerFactory.getLogger(SecureChannelSession.class);

    public static final int SC_SECRET_LENGTH = 16;
    public static final int SC_BLOCK_SIZE = 16;
    public static final int IV_SIZE = 16;
    public static final int MAC_SIZE = 20;

    // secure channel constants
    private final static byte INS_INIT_SECURE_CHANNEL = (byte) 0x81;
    private final static byte INS_PROCESS_SECURE_CHANNEL = (byte) 0x82;
    private final static short SW_SECURE_CHANNEL_REQUIRED = (short) 0x9C20;
    private final static short SW_SECURE_CHANNEL_UNINITIALIZED = (short) 0x9C21;
    private final static short SW_SECURE_CHANNEL_WRONG_IV = (short) 0x9C22;
    private final static short SW_SECURE_CHANNEL_WRONG_MAC = (short) 0x9C23;

    private boolean initialized_secure_channel = false;

    // secure channel keys
    private byte[] secret;
    private byte[] iv;
    private int ivCounter;
    byte[] derived_key;
    byte[] mac_key;

    // for ECDH
    private SecretPoint secretPoint;
    private final ECKey eckey;

    // for session encryption
    private final SecureRandom random;
    private final AESKeyCrypter aesCipher;

    /**
     * Constructs a SecureChannel session on the client.
     */
    public SecureChannelSession() {
        random = new SecureRandom();

        // generate keypair
        eckey = new ECKey();
        aesCipher = new AESKeyCrypter();
    }

    /**
     * Generates a pairing secret. This should be called before each session. The public key of the card is used as input
     * for the EC-DH algorithm. The output is stored as the secret.
     *
     * @param pubkeyData the public key returned by the applet as response to the SELECT command
     */
    public void initiateSecureChannel(byte[] pubkeyData) { //TODO: check keyData format
        try {
            byte[] privkeyData = this.eckey.getPrivKeyBytes();
            secretPoint = new SecretPoint(privkeyData, pubkeyData);
            secret = secretPoint.ECDHSecretAsBytes();
            //log.trace("SATOCHIP SecureChannelSession initiateSecureChannel() secret: " + Utils.bytesToHex(secret));

            // derive session encryption key
            byte[] msg_key = "sc_key".getBytes();
            byte[] derived_key_2Ob = getHmacSha1Hash(secret, msg_key);
            derived_key = new byte[16];
            System.arraycopy(derived_key_2Ob, 0, derived_key, 0, 16);
            //log.trace("SATOCHIP SecureChannelSession initiateSecureChannel() derived_key: " + Utils.bytesToHex(derived_key));
            // derive session mac key
            byte[] msg_mac = "sc_mac".getBytes();
            mac_key = getHmacSha1Hash(secret, msg_mac);
            //log.trace("SATOCHIP SecureChannelSession initiateSecureChannel() mac_key: " + Utils.bytesToHex(mac_key));

            ivCounter = 1;
            initialized_secure_channel = true;
        } catch(Exception e) {
            log.error("Error initiating secure channel", e);
        }
    }

    public APDUCommand encryptSecureChannel(APDUCommand plainApdu) throws CardException {
        try {
            byte[] plainBytes = plainApdu.serialize();

            // set iv
            iv = new byte[SC_BLOCK_SIZE];
            random.nextBytes(iv);
            ByteBuffer bb = ByteBuffer.allocate(4);
            bb.putInt(ivCounter);  // big endian
            byte[] ivCounterBytes = bb.array();
            System.arraycopy(ivCounterBytes, 0, iv, 12, 4);
            ivCounter += 2;

            // encrypt data
            Key aesKey = new Key(derived_key, null, null);
            byte[] encrypted = aesCipher.encrypt(plainBytes, iv, aesKey).getEncryptedBytes();

            // mac
            int offset = 0;
            byte[] data_to_mac = new byte[IV_SIZE + 2 + encrypted.length];
            System.arraycopy(iv, offset, data_to_mac, offset, IV_SIZE);
            offset += IV_SIZE;
            data_to_mac[offset++] = (byte) (encrypted.length >> 8);
            data_to_mac[offset++] = (byte) (encrypted.length % 256);
            System.arraycopy(encrypted, 0, data_to_mac, offset, encrypted.length);
            // log.trace("SATOCHIP data_to_mac: "+ SatochipParser.toHexString(data_to_mac));
            byte[] mac = getHmacSha1Hash(mac_key, data_to_mac);

            // copy all data to new data buffer
            offset = 0;
            byte[] data = new byte[IV_SIZE + 2 + encrypted.length + 2 + MAC_SIZE];
            System.arraycopy(iv, offset, data, offset, IV_SIZE);
            offset += IV_SIZE;
            data[offset++] = (byte) (encrypted.length >> 8);
            data[offset++] = (byte) (encrypted.length % 256);
            System.arraycopy(encrypted, 0, data, offset, encrypted.length);
            offset += encrypted.length;
            data[offset++] = (byte) (mac.length >> 8);
            data[offset++] = (byte) (mac.length % 256);
            System.arraycopy(mac, 0, data, offset, mac.length);

            // convert to C-APDU
            return new APDUCommand(0xB0, INS_PROCESS_SECURE_CHANNEL, 0x00, 0x00, data);
        } catch(Exception e) {
            throw new CardException("Error encrypting secure channel", e);
        }
    }

    public APDUResponse decryptSecureChannel(APDUResponse encryptedApdu) throws CardException {
        try {
            byte[] encryptedBytes = encryptedApdu.getData();
            if(encryptedBytes.length == 0) {
                return encryptedApdu; // no decryption needed
            } else if(encryptedBytes.length < 40) {
                // has at least (IV_SIZE + 2 + 2 + 20)
                throw new RuntimeException("Encrypted response has wrong length: " + encryptedBytes.length);
            }

            int offset = 0;
            byte[] iv = new byte[IV_SIZE];
            System.arraycopy(encryptedBytes, offset, iv, 0, IV_SIZE);
            offset += IV_SIZE;
            int ciphertext_size = ((encryptedBytes[offset++] & 0xff) << 8) + (encryptedBytes[offset++] & 0xff);
            if((encryptedBytes.length - offset) != ciphertext_size) {
                throw new RuntimeException("Encrypted response has wrong length ciphertext_size: " + ciphertext_size);
            }
            byte[] ciphertext = new byte[ciphertext_size];
            System.arraycopy(encryptedBytes, offset, ciphertext, 0, ciphertext.length);

            // decrypt data
            Key aesKey = new Key(derived_key, null, null);
            EncryptedData encryptedData = new EncryptedData(iv, ciphertext, null, null);
            byte[] decrypted = aesCipher.decrypt(encryptedData, aesKey);

            return new APDUResponse(decrypted, (byte) 0x90, (byte) 0x00);
        } catch(Exception e) {
            throw new CardException("Error decrypting secure channel", e);
        }
    }

    public boolean initializedSecureChannel() {
        return initialized_secure_channel;
    }

    public byte[] getPublicKey() {
        return eckey.getPubKey(false);
    }

    public void resetSecureChannel() {
        initialized_secure_channel = false;
    }

    public static byte[] getHmacSha1Hash(byte[] key, byte[] data) {
        try {
            SecretKeySpec secretKeySpec = new SecretKeySpec(key, "HmacSHA1");
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(secretKeySpec);
            return mac.doFinal(data);
        } catch(Exception e) {
            throw new RuntimeException("Error computing HmacSHA1", e);
        }
    }
}
