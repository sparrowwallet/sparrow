package com.sparrowwallet.sparrow.io.keycard;

import com.sparrowwallet.drongo.Drongo;

import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.io.IOException;
import java.util.Arrays;

/**
 * This class is used to send APDU to the applet. Each method corresponds to an APDU as defined in the APPLICATION.md
 * file. Some APDUs map to multiple methods for the sake of convenience since their payload or response require some
 * pre/post processing.
 */
public class KeycardCommandSet {
    static final byte INS_INIT = (byte) 0xFE;
    static final byte INS_FACTORY_RESET = (byte) 0xFD;
    static final byte INS_GET_STATUS = (byte) 0xF2;
    static final byte INS_SET_NDEF = (byte) 0xF3;
    static final byte INS_IDENTIFY_CARD = (byte) 0x14;
    static final byte INS_VERIFY_PIN = (byte) 0x20;
    static final byte INS_CHANGE_PIN = (byte) 0x21;
    static final byte INS_UNBLOCK_PIN = (byte) 0x22;
    static final byte INS_LOAD_KEY = (byte) 0xD0;
    static final byte INS_DERIVE_KEY = (byte) 0xD1;
    static final byte INS_GENERATE_MNEMONIC = (byte) 0xD2;
    static final byte INS_REMOVE_KEY = (byte) 0xD3;
    static final byte INS_GENERATE_KEY = (byte) 0xD4;
    static final byte INS_SIGN = (byte) 0xC0;
    static final byte INS_SET_PINLESS_PATH = (byte) 0xC1;
    static final byte INS_EXPORT_KEY = (byte) 0xC2;
    static final byte INS_GET_DATA = (byte) 0xCA;
    static final byte INS_STORE_DATA = (byte) 0xE2;

    public static final byte CHANGE_PIN_P1_USER_PIN = 0x00;
    public static final byte CHANGE_PIN_P1_PUK = 0x01;
    public static final byte CHANGE_PIN_P1_PAIRING_SECRET = 0x02;

    public static final byte GET_STATUS_P1_APPLICATION = 0x00;
    public static final byte GET_STATUS_P1_KEY_PATH = 0x01;

    public static final byte LOAD_KEY_P1_EC = 0x01;
    public static final byte LOAD_KEY_P1_EXT_EC = 0x02;
    public static final byte LOAD_KEY_P1_SEED = 0x03;

    public static final byte DERIVE_P1_SOURCE_MASTER = (byte) 0x00;
    public static final byte DERIVE_P1_SOURCE_PARENT = (byte) 0x40;
    public static final byte DERIVE_P1_SOURCE_CURRENT = (byte) 0x80;

    static final byte SIGN_P1_CURRENT_KEY = 0x00;
    static final byte SIGN_P1_DERIVE = 0x01;
    static final byte SIGN_P1_DERIVE_AND_MAKE_CURRENT = 0x02;
    static final byte SIGN_P1_PINLESS = 0x03;

    public static final byte SIGN_P2_ECDSA = 0x00;
    public static final byte SIGN_P2_BLS12_381 = 0x01;

    public static final byte STORE_DATA_P1_PUBLIC = 0x00;
    public static final byte STORE_DATA_P1_NDEF = 0x01;
    public static final byte STORE_DATA_P1_CASH = 0x02;

    public static final int GENERATE_MNEMONIC_12_WORDS = 0x04;
    public static final int GENERATE_MNEMONIC_15_WORDS = 0x05;
    public static final int GENERATE_MNEMONIC_18_WORDS = 0x06;
    public static final int GENERATE_MNEMONIC_21_WORDS = 0x07;
    public static final int GENERATE_MNEMONIC_24_WORDS = 0x08;

    static final byte EXPORT_KEY_P1_CURRENT = 0x00;
    static final byte EXPORT_KEY_P1_DERIVE = 0x01;
    static final byte EXPORT_KEY_P1_DERIVE_AND_MAKE_CURRENT = 0x02;

    public static final byte EXPORT_KEY_P2_PRIVATE_AND_PUBLIC = 0x00;
    public static final byte EXPORT_KEY_P2_PUBLIC_ONLY = 0x01;
    public static final byte EXPORT_KEY_P2_EXTENDED_PUBLIC = 0x02;

    static final byte FACTORY_RESET_P1_MAGIC = (byte) 0xAA;
    static final byte FACTORY_RESET_P2_MAGIC = 0x55;

    static final byte TLV_APPLICATION_INFO_TEMPLATE = (byte) 0xA4;

    private final CardChannel apduChannel;
    private SecureChannelSession secureChannel;
    private ApplicationInfo info;

    /**
     * Creates a KeycardCommandSet using the given APDU Channel
     *
     * @param apduChannel APDU channel
     */
    public KeycardCommandSet(CardChannel apduChannel) {
        this.apduChannel = apduChannel;
        this.secureChannel = new SecureChannelSession();
    }

    /**
     * Returns the application info as stored from the last sent SELECT command. Returns null if no succesful SELECT
     * command has been sent using this command set.
     *
     * @return the application info object
     */
    public ApplicationInfo getApplicationInfo() {
        return info;
    }

    /**
     * Set the SecureChannel object
     *
     * @param secureChannel secure channel
     */
    protected void setSecureChannel(SecureChannelSession secureChannel) {
        this.secureChannel = secureChannel;
    }

    /**
     * Returns the current pairing data.
     */
    public Pairing getPairing() {
        return secureChannel.getPairing();
    }

    /**
     * Sets the pairing data.
     *
     * @param pairing data from an existing pairing
     */
    public void setPairing(Pairing pairing) {
        secureChannel.setPairing(pairing);
    }

    /**
     * Selects the default instance of the Keycard applet. The applet is assumed to have been installed with its default
     * AID. The returned data is a public key which must be used to initialize the secure channel.
     *
     * @return the raw card response
     * @throws IOException communication error
     */
    public APDUResponse select() throws IOException {
        return select(Identifiers.KEYCARD_DEFAULT_INSTANCE_IDX);
    }

    /**
     * Selects a Keycard instance. The applet is assumed to have been installed with its default AID. The returned data is
     * a public key which must be used to initialize the secure channel.
     *
     * @param instanceIdx the instance index
     * @return the raw card response
     * @throws IOException communication error
     */
    public APDUResponse select(int instanceIdx) throws IOException {
        APDUCommand selectApplet = new APDUCommand(0x00, 0xA4, 4, 0, Identifiers.getKeycardInstanceAID(instanceIdx));
        APDUResponse resp = apduChannel.send(selectApplet);


        if(resp.getSw() == 0x9000) {
            info = new ApplicationInfo(resp.getData());

            if(info.hasSecureChannelCapability()) {
                this.secureChannel.generateSecret(info.getSecureChannelPubKey());
                this.secureChannel.reset();
            }
        }

        return resp;
    }

    /**
     * Opens the secure channel. Calls the corresponding method of the SecureChannel class.
     *
     * @throws IOException   communication error
     * @throws APDUException secure channel error
     */
    public void autoOpenSecureChannel() throws IOException, APDUException {
        secureChannel.autoOpenSecureChannel(apduChannel);
    }

    /**
     * Automatically pairs. Derives the secret from the given password.
     *
     * @throws IOException   communication error
     * @throws APDUException pairing error
     */
    public void autoPair(String pairingPassword) throws IOException, APDUException {
        byte[] secret = pairingPasswordToSecret(pairingPassword);

        secureChannel.autoPair(apduChannel, secret);
    }

    /**
     * Converts a pairing password to a binary pairing secret.
     *
     * @param pairingPassword the pairing password
     * @return the pairing secret
     */
    public byte[] pairingPasswordToSecret(String pairingPassword) {
        SecretKey key;

        try {
            SecretKeyFactory skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256", Drongo.getProvider());
            PBEKeySpec spec = new PBEKeySpec(pairingPassword.toCharArray(), "Keycard Pairing Password Salt".getBytes(), apduChannel.pairingPasswordPBKDF2IterationCount(), 32 * 8);
            key = skf.generateSecret(spec);
        } catch(Exception e) {
            throw new RuntimeException(e);
        }
        return key.getEncoded();
    }

    /**
     * Automatically pairs. Calls the corresponding method of the SecureChannel class.
     *
     * @throws IOException   communication error
     * @throws APDUException pairing error
     */
    public void autoPair(byte[] sharedSecret) throws IOException, APDUException {
        secureChannel.autoPair(apduChannel, sharedSecret);
    }

    /**
     * Automatically unpairs. Calls the corresponding method of the SecureChannel class.
     *
     * @throws IOException   communication error
     * @throws APDUException unpairing error
     */
    public void autoUnpair() throws IOException, APDUException {
        secureChannel.autoUnpair(apduChannel);
    }

    /**
     * Sends a OPEN SECURE CHANNEL APDU. Calls the corresponding method of the SecureChannel class.
     */
    public APDUResponse openSecureChannel(byte index, byte[] data) throws IOException {
        return secureChannel.openSecureChannel(apduChannel, index, data);
    }

    /**
     * Sends a MUTUALLY AUTHENTICATE APDU. Calls the corresponding method of the SecureChannel class.
     */
    public APDUResponse mutuallyAuthenticate() throws IOException {
        return secureChannel.mutuallyAuthenticate(apduChannel);
    }

    /**
     * Sends a MUTUALLY AUTHENTICATE APDU. Calls the corresponding method of the SecureChannel class.
     */
    public APDUResponse mutuallyAuthenticate(byte[] data) throws IOException {
        return secureChannel.mutuallyAuthenticate(apduChannel, data);
    }

    /**
     * Sends a PAIR APDU. Calls the corresponding method of the SecureChannel class.
     */
    public APDUResponse pair(byte p1, byte[] data) throws IOException {
        return secureChannel.pair(apduChannel, p1, data);
    }

    /**
     * Sends a UNPAIR APDU. Calls the corresponding method of the SecureChannel class.
     */
    public APDUResponse unpair(byte p1) throws IOException {
        return secureChannel.unpair(apduChannel, p1);
    }

    /**
     * Unpair all other clients.
     *
     * @throws IOException   communication error
     * @throws APDUException unpairing error
     */
    public void unpairOthers() throws IOException, APDUException {
        secureChannel.unpairOthers(apduChannel);
    }

    /**
     * Sends an IDENTIFY CARD APDU. The challenge is sent as APDU data as-is. It must be 32 bytes long
     *
     * @param challenge the data of the APDU
     * @return the raw card response
     * @throws IOException communication error
     */
    public APDUResponse identifyCard(byte[] challenge) throws IOException {
        APDUCommand identifyCard = secureChannel.protectedCommand(0x80, INS_IDENTIFY_CARD, 0, 0, challenge);
        return secureChannel.transmit(apduChannel, identifyCard);
    }

    /**
     * Sends a GET STATUS APDU. The info byte is the P1 parameter of the command, valid constants are defined in the applet
     * class itself.
     *
     * @param info the P1 of the APDU
     * @return the raw card response
     * @throws IOException communication error
     */
    public APDUResponse getStatus(byte info) throws IOException {
        APDUCommand getStatus = secureChannel.protectedCommand(0x80, INS_GET_STATUS, info, 0, new byte[0]);
        return secureChannel.transmit(apduChannel, getStatus);
    }

    /**
     * Sends a VERIFY PIN APDU. The raw bytes of the given string are encrypted using the secure channel and used as APDU
     * data.
     *
     * @param pin the PIN
     * @return the raw card response
     * @throws IOException communication error
     */
    public APDUResponse verifyPIN(String pin) throws IOException {
        APDUCommand verifyPIN = secureChannel.protectedCommand(0x80, INS_VERIFY_PIN, 0, 0, pin.getBytes());
        return secureChannel.transmit(apduChannel, verifyPIN);
    }

    /**
     * Sends a CHANGE PIN APDU to change the user PIN.
     *
     * @param pin the new PIN
     * @return the raw card response
     * @throws IOException communication error
     */
    public APDUResponse changePIN(String pin) throws IOException {
        return changePIN(CHANGE_PIN_P1_USER_PIN, pin.getBytes());
    }

    /**
     * Sends a CHANGE PIN APDU to change the PUK.
     *
     * @param puk the new PUK
     * @return the raw card response
     * @throws IOException communication error
     */
    public APDUResponse changePUK(String puk) throws IOException {
        return changePIN(CHANGE_PIN_P1_PUK, puk.getBytes());
    }

    /**
     * Sends a CHANGE PIN APDU to change the pairing password. This does not break existing pairings, but new pairings
     * will be made using the new password.
     *
     * @param pairingPassword the new pairing password
     * @return the raw card response
     * @throws IOException communication error
     */
    public APDUResponse changePairingPassword(String pairingPassword) throws IOException {
        return changePIN(CHANGE_PIN_P1_PAIRING_SECRET, pairingPasswordToSecret(pairingPassword));
    }

    /**
     * Sends a CHANGE PIN APDU. The raw bytes of the given string are encrypted using the secure channel and used as APDU
     * data.
     *
     * @param pinType the PIN type
     * @param pin     the new PIN
     * @return the raw card response
     * @throws IOException communication error
     */
    public APDUResponse changePIN(int pinType, String pin) throws IOException {
        return changePIN(pinType, pin.getBytes());
    }

    /**
     * Sends a CHANGE PIN APDU. The raw bytes of the given string are encrypted using the secure channel and used as APDU
     * data.
     *
     * @param pinType the PIN type
     * @param pin     the new PIN
     * @return the raw card response
     * @throws IOException communication error
     */
    public APDUResponse changePIN(int pinType, byte[] pin) throws IOException {
        APDUCommand changePIN = secureChannel.protectedCommand(0x80, INS_CHANGE_PIN, pinType, 0, pin);
        return secureChannel.transmit(apduChannel, changePIN);
    }

    /**
     * Sends an UNBLOCK PIN APDU. The PUK and PIN are concatenated and the raw bytes are encrypted using the secure
     * channel and used as APDU data.
     *
     * @return the raw card response
     * @throws IOException communication error
     */
    public APDUResponse unblockPIN(String puk, String newPin) throws IOException {
        APDUCommand unblockPIN = secureChannel.protectedCommand(0x80, INS_UNBLOCK_PIN, 0, 0, (puk + newPin).getBytes());
        return secureChannel.transmit(apduChannel, unblockPIN);
    }

    /**
     * Sends a LOAD KEY APDU. The given seed is sent as-is and the P1 of the command is set to LOAD_KEY_P1_SEED (0x03).
     * This works on cards which support public key derivation. The loaded keyset is extended and support further
     * key derivation.
     *
     * @param seed the binary seed
     * @return the raw card response
     * @throws IOException communication error
     */
    public APDUResponse loadKey(byte[] seed) throws IOException {
        return loadKey(seed, LOAD_KEY_P1_SEED);
    }

    /**
     * Sends a LOAD KEY APDU. The key is sent in TLV format. The public key is included if not null. The chain code is
     * included if not null. P1 is set automatically to either LOAD_KEY_P1_EC or
     * LOAD_KEY_P1_EXT_EC depending on the presence of the chainCode.
     *
     * @param publicKey  a raw public key
     * @param privateKey a raw private key
     * @param chainCode  the chain code
     * @return the raw card response
     * @throws IOException communication error
     */
    public APDUResponse loadKey(byte[] publicKey, byte[] privateKey, byte[] chainCode) throws IOException {
        return loadKey(new BIP32KeyPair(privateKey, chainCode, publicKey), publicKey == null);
    }

    public APDUResponse loadKey(BIP32KeyPair keyPair) throws IOException {
        return loadKey(keyPair, false);
    }

    public APDUResponse loadKey(BIP32KeyPair keyPair, boolean omitPublic) throws IOException {
        byte p1;

        if(keyPair.isExtended()) {
            p1 = LOAD_KEY_P1_EXT_EC;
        } else {
            p1 = LOAD_KEY_P1_EC;
        }

        return loadKey(keyPair.toTLV(!omitPublic), p1);
    }

    /**
     * Sends a LOAD KEY APDU. The data is encrypted and sent as-is. The keyType parameter is used as P1.
     *
     * @param data    key data
     * @param keyType the P1 parameter
     * @return the raw card response
     * @throws IOException communication error
     */
    public APDUResponse loadKey(byte[] data, byte keyType) throws IOException {
        APDUCommand loadKey = secureChannel.protectedCommand(0x80, INS_LOAD_KEY, keyType, 0, data);
        return secureChannel.transmit(apduChannel, loadKey);
    }

    /**
     * Sends a GENERATE MNEMONIC APDU. The cs parameter is the length of the checksum and is used as P1.
     *
     * @param cs the P1 parameter
     * @return the raw card response
     * @throws IOException communication error
     */
    public APDUResponse generateMnemonic(int cs) throws IOException {
        APDUCommand generateMnemonic = secureChannel.protectedCommand(0x80, INS_GENERATE_MNEMONIC, cs, 0, new byte[0]);
        return secureChannel.transmit(apduChannel, generateMnemonic);
    }

    /**
     * Sends a REMOVE KEY APDU.
     *
     * @return the raw card response
     * @throws IOException communication error
     */
    public APDUResponse removeKey() throws IOException {
        APDUCommand removeKey = secureChannel.protectedCommand(0x80, INS_REMOVE_KEY, 0, 0, new byte[0]);
        return secureChannel.transmit(apduChannel, removeKey);
    }

    /**
     * Sends a GENERATE KEY APDU.
     *
     * @return the raw card response
     * @throws IOException communication error
     */
    public APDUResponse generateKey() throws IOException {
        APDUCommand generateKey = secureChannel.protectedCommand(0x80, INS_GENERATE_KEY, 0, 0, new byte[0]);
        return secureChannel.transmit(apduChannel, generateKey);
    }

    /**
     * Sends a SIGN APDU. This signs a precomputed hash that must be exactly 32-bytes long.
     *
     * @param hash the hash to sign
     * @return the raw card response
     * @throws IOException communication error
     */
    public APDUResponse sign(byte[] hash) throws IOException {
        return sign(hash, SIGN_P1_CURRENT_KEY);
    }

    /**
     * Sends a SIGN APDU. This signs a precomputed hash that must be exactly 32-bytes long. The key used to sign is given
     * as a parameter.
     *
     * @param hash        the hash to sign
     * @param makeCurrent ture if the key used to sign should become the current key, false otherwise
     * @return the raw card response
     * @throws IOException communication error
     * @params path the path of the key to use
     */
    public APDUResponse signWithPath(byte[] hash, String path, boolean makeCurrent) throws IOException {
        KeyPath keyPath = new KeyPath(path);
        byte[] pathData = keyPath.getData();
        byte[] data = Arrays.copyOf(hash, hash.length + pathData.length);
        System.arraycopy(pathData, 0, data, hash.length, pathData.length);
        return sign(data, keyPath.getSource() | (makeCurrent ? SIGN_P1_DERIVE_AND_MAKE_CURRENT : SIGN_P1_DERIVE));
    }

    /**
     * Sends a SIGN APDU. This signs a precomputed hash that must be exactly 32-bytes long. The pinless path will be used
     * to sign. This command is the only variant of SIGN which can also be executed without a Secure Channel.
     *
     * @param hash the hash to sign
     * @return the raw card response
     * @throws IOException communication error
     */
    public APDUResponse signPinless(byte[] hash) throws IOException {
        return sign(hash, SIGN_P1_PINLESS);
    }

    /**
     * Sends a SIGN APDU. This signs a precomputed hash so the input must be exactly 32-bytes long, eventually followed by
     * a derivation path.
     *
     * @param p1   the p1 parameter
     * @param data the data to sign
     * @return the raw card response
     * @throws IOException communication error
     */
    public APDUResponse sign(byte[] data, int p1) throws IOException {
        APDUCommand sign = secureChannel.protectedCommand(0x80, INS_SIGN, p1, 0x01, data);
        return secureChannel.transmit(apduChannel, sign);
    }

    /**
     * Sends a DERIVE KEY APDU with the given key path.
     *
     * @param keypath the string key path
     * @return the raw card response
     * @throws IOException communication error
     */
    public APDUResponse deriveKey(String keypath) throws IOException {
        KeyPath path = new KeyPath(keypath);
        return deriveKey(path.getData(), path.getSource());
    }

    /**
     * Sends a DERIVE KEY APDU. The data is encrypted and sent as-is. The P1 is forced to 0, meaning that the derivation
     * starts from the master key.
     *
     * @param data the raw key path
     * @return the raw card response
     * @throws IOException communication error
     */
    public APDUResponse deriveKey(byte[] data) throws IOException {
        return deriveKey(data, DERIVE_P1_SOURCE_MASTER);
    }

    /**
     * Sends a DERIVE KEY APDU. The data is encrypted and sent as-is. The source parameter is used as P1.
     *
     * @param data   the raw key path or a public key
     * @param source the source to start derivation
     * @return the raw card response
     * @throws IOException communication error
     */
    public APDUResponse deriveKey(byte[] data, int source) throws IOException {
        APDUCommand deriveKey = secureChannel.protectedCommand(0x80, INS_DERIVE_KEY, source, 0x00, data);
        return secureChannel.transmit(apduChannel, deriveKey);
    }

    /**
     * Sends a SET PINLESS PATH APDU. The path must be absolute, that is starting from the master key.
     *
     * @param path the path. Must be an absolute path (i.e: starting from the master key)
     * @return the raw card response
     * @throws IOException communication error
     */
    public APDUResponse setPinlessPath(String path) throws IOException {
        KeyPath keyPath = new KeyPath(path);
        if(keyPath.getSource() != DERIVE_P1_SOURCE_MASTER) {
            throw new IllegalArgumentException("Only absolute paths can be set as PINLESS path");
        }

        return setPinlessPath(keyPath.getData());
    }

    /**
     * Sends an empty SET PINLESS PATH APDU, resetting it. After this command the card does not have a PINless path until
     * a new one is set.
     *
     * @return the raw card response
     * @throws IOException communication error
     */
    public APDUResponse resetPinlessPath() throws IOException {
        return setPinlessPath(new byte[]{});
    }

    /**
     * Sends a SET PINLESS PATH APDU. The data is encrypted and sent as-is.
     *
     * @param data the raw key path
     * @return the raw card response
     * @throws IOException communication error
     */
    public APDUResponse setPinlessPath(byte[] data) throws IOException {
        APDUCommand setPinlessPath = secureChannel.protectedCommand(0x80, INS_SET_PINLESS_PATH, 0x00, 0x00, data);
        return secureChannel.transmit(apduChannel, setPinlessPath);
    }

    private byte poToP2(boolean publicOnly) {
        return publicOnly ? EXPORT_KEY_P2_PUBLIC_ONLY : EXPORT_KEY_P2_PRIVATE_AND_PUBLIC;
    }

    /**
     * Sends an EXPORT KEY APDU to export the current key.
     *
     * @param publicOnly exports only the public key
     * @return the raw card reponse
     * @throws IOException communication error
     */
    public APDUResponse exportCurrentKey(boolean publicOnly) throws IOException {
        return exportCurrentKey(poToP2(publicOnly));
    }

    /**
     * Sends an EXPORT KEY APDU to export the current key.
     *
     * @param p2 the p2 parameter
     * @return the raw card reponse
     * @throws IOException communication error
     */
    public APDUResponse exportCurrentKey(byte p2) throws IOException {
        return exportKey(EXPORT_KEY_P1_CURRENT, p2, new byte[0]);
    }

    /**
     * Sends an EXPORT KEY APDU. Performs derivation of the given keypath and optionally makes it the current key.
     *
     * @param keyPath     the keypath to export
     * @param makeCurrent if the key should be made current or not
     * @param publicOnly  the P2 parameter
     * @return the raw card response
     * @throws IOException communication error
     */
    public APDUResponse exportKey(String keyPath, boolean makeCurrent, boolean publicOnly) throws IOException {
        return exportKey(keyPath, makeCurrent, poToP2(publicOnly));
    }

    /**
     * Sends an EXPORT KEY APDU. Performs derivation of the given keypath and optionally makes it the current key.
     *
     * @param keyPath     the keypath to export
     * @param makeCurrent if the key should be made current or not
     * @param p2          the P2 parameter
     * @return the raw card response
     * @throws IOException communication error
     */
    public APDUResponse exportKey(String keyPath, boolean makeCurrent, byte p2) throws IOException {
        KeyPath path = new KeyPath(keyPath);
        return exportKey(path.getData(), path.getSource(), makeCurrent, p2);
    }

    /**
     * Sends an EXPORT KEY APDU. Performs derivation of the given keypath and optionally makes it the current key.
     *
     * @param keyPath     the keypath to export
     * @param makeCurrent if the key should be made current or not
     * @param publicOnly  the P2 parameter
     * @return the raw card response
     * @throws IOException communication error
     */
    public APDUResponse exportKey(byte[] keyPath, int source, boolean makeCurrent, boolean publicOnly) throws IOException {
        return exportKey(keyPath, source, makeCurrent, poToP2(publicOnly));
    }

    /**
     * Sends an EXPORT KEY APDU. Performs derivation of the given keypath and optionally makes it the current key.
     *
     * @param keyPath     the keypath to export
     * @param makeCurrent if the key should be made current or not
     * @param p2          the P2 parameter
     * @return the raw card response
     * @throws IOException communication error
     */
    public APDUResponse exportKey(byte[] keyPath, int source, boolean makeCurrent, byte p2) throws IOException {
        int p1 = source | (makeCurrent ? EXPORT_KEY_P1_DERIVE_AND_MAKE_CURRENT : EXPORT_KEY_P1_DERIVE);
        return exportKey(p1, p2, keyPath);
    }

    /**
     * Sends an EXPORT KEY APDU. The parameters are sent as-is.
     *
     * @param derivationOptions the P1 parameter
     * @param publicOnly        the P2 parameter
     * @param keypath           the data parameter
     * @return the raw card response
     * @throws IOException communication error
     */
    public APDUResponse exportKey(int derivationOptions, boolean publicOnly, byte[] keypath) throws IOException {
        return exportKey(derivationOptions, poToP2(publicOnly), keypath);
    }

    /**
     * Sends an EXPORT KEY APDU. The parameters are sent as-is.
     *
     * @param derivationOptions the P1 parameter
     * @param p2                the P2 parameter
     * @param keypath           the data parameter
     * @return the raw card response
     * @throws IOException communication error
     */
    public APDUResponse exportKey(int derivationOptions, byte p2, byte[] keypath) throws IOException {
        APDUCommand exportKey = secureChannel.protectedCommand(0x80, INS_EXPORT_KEY, derivationOptions, p2, keypath);
        return secureChannel.transmit(apduChannel, exportKey);
    }

    /**
     * Sends a GET DATA APDU.
     *
     * @param dataType the type of data to be stored
     * @return the raw card response
     * @throws IOException communication error
     */
    public APDUResponse getData(byte dataType) throws IOException {
        APDUCommand getData = secureChannel.protectedCommand(0x80, INS_GET_DATA, dataType, 0, new byte[0]);
        return secureChannel.transmit(apduChannel, getData);
    }

    /**
     * Sends a STORE DATA APDU for NDEF.
     *
     * @param ndef the data field of the APDU
     * @return the raw card response
     * @throws IOException communication error
     */
    public APDUResponse setNDEF(byte[] ndef) throws IOException {
        if((info.getAppVersion() >> 8) > 2) {
            if((ndef.length - 2) != ((ndef[0] << 8) | ndef[1])) {
                byte[] tmp = new byte[ndef.length + 2];
                tmp[0] = (byte) (ndef.length >> 8);
                tmp[1] = (byte) (ndef.length & 0xff);
                System.arraycopy(ndef, 0, tmp, 2, ndef.length);
                ndef = tmp;
            }

            return storeData(ndef, STORE_DATA_P1_NDEF);
        } else {
            APDUCommand setNDEF = secureChannel.protectedCommand(0x80, INS_SET_NDEF, 0, 0, ndef);
            return secureChannel.transmit(apduChannel, setNDEF);
        }
    }

    /**
     * Sends a STORE DATA APDU.
     *
     * @param data     the data field of the APDU
     * @param dataType the type of data to be stored
     * @return the raw card response
     * @throws IOException communication error
     */
    public APDUResponse storeData(byte[] data, byte dataType) throws IOException {
        APDUCommand storeData = secureChannel.protectedCommand(0x80, INS_STORE_DATA, dataType, 0, data);
        return secureChannel.transmit(apduChannel, storeData);
    }

    /**
     * Sends the INIT command to the card. If either pinRetries or pukRetries is zero, neither will be sent.
     *
     * @param pin             the PIN
     * @param puk             the PUK
     * @param pairingPassword pairing password
     * @return the raw card response
     * @throws IOException communication error
     */
    public APDUResponse init(String pin, String puk, String pairingPassword) throws IOException {
        return this.init(pin, puk, pairingPassword, (byte) 0, (byte) 0);
    }

    /**
     * Sends the INIT command to the card.
     *
     * @param pin             the PIN
     * @param puk             the PUK
     * @param pairingPassword pairing password
     * @param pinRetries      the number of allowed PIN retries
     * @param pukRetries      the number of allowed PUK retries
     * @return the raw card response
     * @throws IOException communication error
     */
    public APDUResponse init(String pin, String puk, String pairingPassword, byte pinRetries, byte pukRetries) throws IOException {
        return this.init(pin, null, puk, pairingPasswordToSecret(pairingPassword), pinRetries, pukRetries);
    }

    /**
     * Sends the INIT command to the card.
     *
     * @param pin             the PIN
     * @param altPin          the alternative PIN
     * @param puk             the PUK
     * @param pairingPassword pairing password
     * @param pinRetries      the number of allowed PIN retries
     * @param pukRetries      the number of allowed PUK retries
     * @return the raw card response
     * @throws IOException communication error
     */
    public APDUResponse init(String pin, String altPin, String puk, String pairingPassword, byte pinRetries, byte pukRetries) throws IOException {
        return this.init(pin, altPin, puk, pairingPasswordToSecret(pairingPassword), pinRetries, pukRetries);
    }

    /**
     * Sends the INIT command to the card.
     *
     * @param pin          the PIN
     * @param puk          the PUK
     * @param sharedSecret the shared secret for pairing
     * @return the raw card response
     * @throws IOException communication error
     */
    public APDUResponse init(String pin, String puk, byte[] sharedSecret) throws IOException {
        return init(pin, null, puk, sharedSecret, (byte) 0, (byte) 0);
    }

    /**
     * Sends the INIT command to the card. If either pinRetries or pukRetries is zero, neither will be sent.
     *
     * @param pin          the PIN
     * @param pin          the alternative
     * @param puk          the PUK
     * @param sharedSecret the shared secret for pairing
     * @param pinRetries   the number of allowed PIN retries
     * @param pukRetries   the number of allowed PUK retries
     * @return the raw card response
     * @throws IOException communication error
     */
    public APDUResponse init(String pin, String altPin, String puk, byte[] sharedSecret, byte pinRetries, byte pukRetries) throws IOException {
        int baselen = pin.length() + puk.length() + sharedSecret.length;
        int extlen;

        if(altPin != null) {
            extlen = 2 + altPin.length();
        } else if((pinRetries != 0) || (pukRetries != 0)) {
            extlen = 2;
        } else {
            extlen = 0;
        }

        byte[] initData = Arrays.copyOf(pin.getBytes(), baselen + extlen);
        System.arraycopy(puk.getBytes(), 0, initData, pin.length(), puk.length());
        System.arraycopy(sharedSecret, 0, initData, pin.length() + puk.length(), sharedSecret.length);

        if(extlen > 0) {
            initData[baselen] = pinRetries;
            initData[baselen + 1] = pukRetries;

            if(extlen > 2) {
                System.arraycopy(altPin.getBytes(), 0, initData, baselen + 2, altPin.length());
            }
        }

        APDUCommand init = new APDUCommand(0x80, INS_INIT, 0, 0, secureChannel.oneShotEncrypt(initData));
        return apduChannel.send(init);
    }

    /**
     * Sends the FACTORY RESET command to the card.
     *
     * @return the raw card response
     * @throws IOException communication error
     */
    public APDUResponse factoryReset() throws IOException {
        APDUCommand factoryReset = new APDUCommand(0x80, INS_FACTORY_RESET, FACTORY_RESET_P1_MAGIC, FACTORY_RESET_P2_MAGIC, new byte[0]);
        return apduChannel.send(factoryReset);
    }
}
