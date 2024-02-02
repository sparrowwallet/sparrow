package com.sparrowwallet.sparrow.io.satochip;

import com.sparrowwallet.drongo.ExtendedKey;
import com.sparrowwallet.drongo.Utils;
import com.sparrowwallet.drongo.protocol.Base58;

import com.sparrowwallet.sparrow.io.CardAuthorizationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.*;
import java.nio.charset.StandardCharsets;

import java.security.SecureRandom;

import static com.sparrowwallet.sparrow.io.satochip.Constants.*;

import javax.smartcardio.*;

/**
 * This class is used to send APDU to the applet. Each method corresponds to an APDU as defined in the APPLICATION.md
 * file. Some APDUs map to multiple methods for the sake of convenience since their payload or response require some
 * pre/post processing.
 */
public class SatochipCommandSet {

    private static final Logger log = LoggerFactory.getLogger(SatochipCommandSet.class);

    private final SatoCardTransport cardTransport;
    private final SecureChannelSession secureChannel;
    private SatoCardStatus status;
    private SatochipParser parser = null;

    private String pinCached = null;

    public static final byte[] SATOCHIP_AID = Utils.hexToBytes("5361746f43686970"); //SatoChip

    /**
     * Creates a SatochipCommandSet using the given APDU Channel
     */
    public SatochipCommandSet() throws CardException {
        this.cardTransport = new SatoCardTransport(SATOCHIP_AID);
        this.secureChannel = new SecureChannelSession();
        this.parser = new SatochipParser();
    }

    /**
     * Returns the application info as stored from the last sent SELECT command. Returns null if no successful SELECT
     * command has been sent using this command set.
     *
     * @return the application info object
     */
    public SatoCardStatus getApplicationStatus() {
        if(this.status == null) {
            this.cardGetStatus();
        }

        return this.status;
    }

    /****************************************
     *                AUTHENTIKEY           *
     ****************************************/

    public APDUResponse cardTransmit(APDUCommand plainApdu) {
        // we try to transmit the APDU until we receive the answer or we receive an unrecoverable error
        boolean isApduTransmitted = false;
        do {
            try {
                byte[] apduBytes = plainApdu.serialize();
                byte ins = apduBytes[1];
                boolean isEncrypted = false;

                // check if status available
                if(status == null) {
                    APDUCommand statusCapdu = new APDUCommand(0xB0, INS_GET_STATUS, 0x00, 0x00, new byte[0]);
                    APDUResponse statusRapdu = this.cardTransport.send(statusCapdu);
                    status = new SatoCardStatus(statusRapdu);
                }

                APDUCommand capdu = plainApdu;
                if(status.needsSecureChannel() && (ins != 0xA4) && (ins != 0x81) && (ins != 0x82) && (ins != INS_GET_STATUS)) {
                    if(!secureChannel.initializedSecureChannel()) {
                        // get card's public key
                        APDUResponse secChannelRapdu = this.cardInitiateSecureChannel();
                        byte[] pubkey = this.parser.parseInitiateSecureChannel(secChannelRapdu);
                        // setup secure channel
                        this.secureChannel.initiateSecureChannel(pubkey);
                    }
                    // encrypt apdu
                    capdu = secureChannel.encryptSecureChannel(plainApdu);
                    isEncrypted = true;
                }

                APDUResponse rapdu = this.cardTransport.send(capdu);
                int sw12 = rapdu.getSw();

                // check answer
                if(sw12 == 0x9000) { // ok!
                    if(isEncrypted) {
                        // decrypt
                        rapdu = secureChannel.decryptSecureChannel(rapdu);
                    }
                    isApduTransmitted = true; // leave loop
                    return rapdu;
                }
                // PIN authentication is required
                else if(sw12 == 0x9C06) {
                    //cardVerifyPIN();
                    log.error("Error, Satochip PIN required");
                    throw new CardAuthorizationException("PIN is required");
                }
                // SecureChannel is not initialized
                else if(sw12 == 0x9C21) {
                    log.error("Error, Satochip secure channel required");
                    secureChannel.resetSecureChannel();
                } else {
                    // cannot resolve issue at this point
                    isApduTransmitted = true; // leave loop
                    return rapdu;
                }
            } catch(Exception e) {
                log.warn("Error transmitting Satochip command set" + e);
                return new APDUResponse(new byte[0], (byte) 0x00, (byte) 0x00); // return empty APDUResponse
            }
        } while(!isApduTransmitted);

        return new APDUResponse(new byte[0], (byte) 0x00, (byte) 0x00); // should not happen
    }

    public void cardDisconnect() {
        secureChannel.resetSecureChannel();
        status = null;
        pinCached = null;
        try {
            cardTransport.disconnect();
        } catch(CardException e) {
            log.error("Error disconnecting Satochip" + e);
        }
    }

    public void cardGetStatus() {
        APDUCommand plainApdu = new APDUCommand(0xB0, INS_GET_STATUS, 0x00, 0x00, new byte[0]);
        APDUResponse respApdu = this.cardTransmit(plainApdu);
        this.status = new SatoCardStatus(respApdu);
    }

    public APDUResponse cardInitiateSecureChannel() throws CardException {
        byte[] pubkey = secureChannel.getPublicKey();
        APDUCommand plainApdu = new APDUCommand(0xB0, INS_INIT_SECURE_CHANNEL, 0x00, 0x00, pubkey);

        return this.cardTransport.send(plainApdu);
    }

    /****************************************
     *               CARD MGMT               *
     ****************************************/

    public APDUResponse cardSetup(byte pin_tries0, byte[] pin0) {
        // use random values for pin1, ublk0, ublk1
        SecureRandom random = new SecureRandom();
        byte[] ublk0 = new byte[8];
        byte[] ublk1 = new byte[8];
        byte[] pin1 = new byte[8];
        random.nextBytes(ublk0);
        random.nextBytes(ublk1);
        random.nextBytes(pin1);

        byte ublk_tries0 = (byte) 0x01;
        byte ublk_tries1 = (byte) 0x01;
        byte pin_tries1 = (byte) 0x01;

        return cardSetup(pin_tries0, ublk_tries0, pin0, ublk0, pin_tries1, ublk_tries1, pin1, ublk1);
    }

    public APDUResponse cardSetup(byte pin_tries0, byte ublk_tries0, byte[] pin0, byte[] ublk0,
            byte pin_tries1, byte ublk_tries1, byte[] pin1, byte[] ublk1) {

        byte[] pin = {0x4D, 0x75, 0x73, 0x63, 0x6C, 0x65, 0x30, 0x30}; //default pin
        byte cla = (byte) 0xB0;
        byte ins = INS_SETUP;
        byte p1 = 0;
        byte p2 = 0;

        // data=[pin_length(1) | pin |
        //        pin_tries0(1) | ublk_tries0(1) | pin0_length(1) | pin0 | ublk0_length(1) | ublk0 |
        //        pin_tries1(1) | ublk_tries1(1) | pin1_length(1) | pin1 | ublk1_length(1) | ublk1 |
        //        memsize(2) | memsize2(2) | ACL(3) |
        //        option_flags(2) | hmacsha160_key(20) | amount_limit(8)]
        int optionsize = 0;
        int option_flags = 0; // do not use option (mostly deprecated)
        int offset = 0;
        int datasize = 16 + pin.length + pin0.length + pin1.length + ublk0.length + ublk1.length + optionsize;
        byte[] data = new byte[datasize];

        data[offset++] = (byte) pin.length;
        System.arraycopy(pin, 0, data, offset, pin.length);
        offset += pin.length;
        // pin0 & ublk0
        data[offset++] = pin_tries0;
        data[offset++] = ublk_tries0;
        data[offset++] = (byte) pin0.length;
        System.arraycopy(pin0, 0, data, offset, pin0.length);
        offset += pin0.length;
        data[offset++] = (byte) ublk0.length;
        System.arraycopy(ublk0, 0, data, offset, ublk0.length);
        offset += ublk0.length;
        // pin1 & ublk1
        data[offset++] = pin_tries1;
        data[offset++] = ublk_tries1;
        data[offset++] = (byte) pin1.length;
        System.arraycopy(pin1, 0, data, offset, pin1.length);
        offset += pin1.length;
        data[offset++] = (byte) ublk1.length;
        System.arraycopy(ublk1, 0, data, offset, ublk1.length);
        offset += ublk1.length;

        // memsize default (deprecated)
        data[offset++] = (byte) 00;
        data[offset++] = (byte) 32;
        data[offset++] = (byte) 00;
        data[offset++] = (byte) 32;

        // ACL (deprecated)
        data[offset++] = (byte) 0x01;
        data[offset++] = (byte) 0x01;
        data[offset++] = (byte) 0x01;

        APDUCommand plainApdu = new APDUCommand(cla, ins, p1, p2, data);
        APDUResponse respApdu = this.cardTransmit(plainApdu);

        if(respApdu.getSw() == 0x9000) {
            //setPin0(pin0); // todo: cache value...
        } else {
            log.error("Error " + respApdu.toHexString());
        }

        return respApdu;
    }


    /****************************************
     *             PIN MGMT                  *
     ****************************************/

    public APDUResponse cardVerifyPIN() throws CardException {
        return this.cardVerifyPIN((byte) 0, pinCached);
    }

    public APDUResponse cardVerifyPIN(int pinNbr, String pin) throws CardException {
        if(pin == null) {
            if(pinCached == null) {
                throw new CardException("PIN required!");
            }
            pin = this.pinCached;
        }

        byte[] pinBytes = pin.getBytes(StandardCharsets.UTF_8);
        if(pinBytes.length > 16) {
            throw new CardException("PIN should be maximum 16 characters!");
        }

        APDUCommand capdu = new APDUCommand(0xB0, INS_VERIFY_PIN, (byte) pinNbr, 0x00, pinBytes);
        APDUResponse rapdu = this.cardTransmit(capdu);

        // correct PIN: cache PIN value
        int sw = rapdu.getSw();
        if(sw == 0x9000) {
            this.pinCached = pin; //set cached PIN value
        }
        // wrong PIN, get remaining tries available (since v0.11)
        else if((sw & 0xffc0) == 0x63c0) {
            this.pinCached = null; //reset cached PIN value
            int pinLeft = (sw & ~0xffc0);
            throw new CardAuthorizationException("Wrong PIN, remaining tries: " + pinLeft);
        }
        // wrong PIN (legacy before v0.11)
        else if(sw == 0x9c02) {
            this.pinCached = null; //reset cached PIN value
            SatoCardStatus cardStatus = this.getApplicationStatus();
            int pinLeft = cardStatus.getPin0RemainingCounter();
            throw new CardAuthorizationException("Wrong PIN, remaining tries: " + pinLeft);
        }
        // blocked PIN
        else if(sw == 0x9c0c) {
            throw new CardException("Card is blocked!");
        }

        return rapdu;
    }

    public void cardChangePIN(int pinNbr, String oldPin, String newPin) throws CardException {
        byte[] oldPinBytes = oldPin.getBytes(StandardCharsets.UTF_8);
        byte[] newPinBytes = newPin.getBytes(StandardCharsets.UTF_8);
        int lc = 1 + oldPinBytes.length + 1 + newPinBytes.length;
        byte[] data = new byte[lc];

        data[0] = (byte) oldPinBytes.length;
        int offset = 1;
        System.arraycopy(oldPinBytes, 0, data, offset, oldPinBytes.length);
        offset += oldPinBytes.length;
        data[offset] = (byte) newPinBytes.length;
        offset += 1;
        System.arraycopy(newPinBytes, 0, data, offset, newPinBytes.length);

        APDUCommand capdu = new APDUCommand(0xB0, INS_CHANGE_PIN, (byte) pinNbr, 0x00, data);
        APDUResponse rapdu = this.cardTransmit(capdu);

        // correct PIN: cache PIN value
        int sw = rapdu.getSw();
        if(sw == 0x9000) {
            this.pinCached = newPin;
        }
        // wrong PIN, get remaining tries available (since v0.11)
        else if((sw & 0xffc0) == 0x63c0) {
            int pinLeft = (sw & ~0xffc0);
            throw new CardAuthorizationException("Wrong PIN, remaining tries: " + pinLeft);
        }
        // wrong PIN (legacy before v0.11)
        else if(sw == 0x9c02) {
            SatoCardStatus cardStatus = this.getApplicationStatus();
            int pinLeft = cardStatus.getPin0RemainingCounter();
            throw new CardAuthorizationException("Wrong PIN, remaining tries: " + pinLeft);
        }
        // blocked PIN
        else if(sw == 0x9c0c) {
            throw new CardException("Card is blocked!");
        }
    }

    /****************************************
     *                 BIP32                *
     ****************************************/

    public APDUResponse cardBip32ImportSeed(byte[] masterseed) {
        APDUCommand plainApdu = new APDUCommand(0xB0, INS_BIP32_IMPORT_SEED, masterseed.length, 0x00, masterseed);
        return this.cardTransmit(plainApdu);
    }

    public APDUResponse cardBip32GetExtendedKey(String stringPath) {
        KeyPath keyPath = new KeyPath(stringPath);
        byte[] bytePath = keyPath.getData();
        return cardBip32GetExtendedKey(bytePath);
    }

    public APDUResponse cardBip32GetExtendedKey(byte[] bytePath) {
        byte p1 = (byte) (bytePath.length / 4);

        APDUCommand capdu = new APDUCommand(0xB0, INS_BIP32_GET_EXTENDED_KEY, p1, 0x40, bytePath);
        APDUResponse rapdu = this.cardTransmit(capdu);
        if(rapdu.getSw() == 0x9C01) {
            // error 0X9C01: no memory available for key derivation => flush memory cache in card
            capdu = new APDUCommand(0xB0, INS_BIP32_GET_EXTENDED_KEY, p1, 0x80, bytePath);
            rapdu = this.cardTransmit(capdu);
        }
        return rapdu;
    }

    /*
     *  Get the BIP32 xpub for given path.
     *
     *  Parameters:
     *  path (str): the path; if given as a string, it will be converted to bytes (4 bytes for each path index)
     *  xtype (str): the type of transaction such as  'standard', 'p2wpkh-p2sh', 'p2wpkh', 'p2wsh-p2sh', 'p2wsh'
     *  is_mainnet (bool): is mainnet or testnet
     *
     *  Return:
     *  xpub (str): the corresponding xpub value
     */
    public String cardBip32GetXpub(String stringPath, ExtendedKey.Header xtype) throws CardException {
        // path is of the form 44'/0'/1'
        KeyPath keyPath = new KeyPath(stringPath);
        byte[] bytePath = keyPath.getData();
        int depth = bytePath.length / 4;

        APDUResponse rapdu = this.cardBip32GetExtendedKey(bytePath);
        byte[][] extendedkey = this.parser.parseBip32GetExtendedKey(rapdu);

        byte[] fingerprint = new byte[4];
        byte[] childNumber = new byte[4];
        if(depth == 0) { //masterkey
            // fingerprint and childnumber set to all-zero bytes by default
            //fingerprint= bytes([0,0,0,0])
            //childNumber= bytes([0,0,0,0])
        } else { //get parent info
            byte[] bytePathParent = Arrays.copyOfRange(bytePath, 0, bytePath.length - 4);
            APDUResponse rapdu2 = this.cardBip32GetExtendedKey(bytePathParent);
            byte[][] extendedkeyParent = this.parser.parseBip32GetExtendedKey(rapdu2);
            byte[] identifier = Utils.sha256hash160(extendedkeyParent[0]);
            fingerprint = Arrays.copyOfRange(identifier, 0, 4);
            childNumber = Arrays.copyOfRange(bytePath, bytePath.length - 4, bytePath.length);
        }

        ByteBuffer buffer = ByteBuffer.allocate(78);
        buffer.putInt(xtype.getHeader());
        buffer.put((byte) depth);
        buffer.put(fingerprint);
        buffer.put(childNumber);
        buffer.put(extendedkey[1]); // chaincode
        buffer.put(extendedkey[0]); // pubkey (compressed)
        byte[] xpubByte = buffer.array();

        return Base58.encodeChecked(xpubByte);
    }

    /****************************************
     *             SIGNATURES               *
     ****************************************/

    public APDUResponse cardSignTransactionHash(byte keynbr, byte[] txhash, byte[] chalresponse) throws CardException {
        byte[] data;
        if(txhash.length != 32) {
            throw new CardException("Wrong txhash length (should be 32)");
        }
        if(chalresponse == null) {
            data = new byte[32];
            System.arraycopy(txhash, 0, data, 0, txhash.length);
        } else if(chalresponse.length == 20) {
            data = new byte[32 + 2 + 20];
            int offset = 0;
            System.arraycopy(txhash, 0, data, offset, txhash.length);
            offset += 32;
            data[offset++] = (byte) 0x80; // 2 middle bytes for 2FA flag
            data[offset++] = (byte) 0x00;
            System.arraycopy(chalresponse, 0, data, offset, chalresponse.length);
        } else {
            throw new CardException("Wrong challenge-response length (should be 20)");
        }

        APDUCommand plainApdu = new APDUCommand(0xB0, INS_SIGN_TRANSACTION_HASH, keynbr, 0x00, data);
        return this.cardTransmit(plainApdu);
    }

    /**
     * This function signs a given hash with a std or the last extended key
     * If 2FA is enabled, a HMAC must be provided as an additional security layer.      *
     * ins: 0x7B
     * p1: key number or 0xFF for the last derived Bip32 extended key
     * p2: 0x00
     * data: [hash(32b) | option: 2FA-flag(2b)|hmac(20b)]
     * return: [sig]
     */
    public APDUResponse cardSignSchnorrHash(byte keynbr, byte[] txhash, byte[] chalresponse) throws CardException {
        byte[] data;
        if(txhash.length != 32) {
            throw new CardException("Wrong txhash length (should be 32)");
        }
        if(chalresponse == null) {
            data = new byte[32];
            System.arraycopy(txhash, 0, data, 0, txhash.length);
        } else if(chalresponse.length == 20) {
            data = new byte[32 + 2 + 20];
            int offset = 0;
            System.arraycopy(txhash, 0, data, offset, txhash.length);
            offset += 32;
            data[offset++] = (byte) 0x80; // 2 middle bytes for 2FA flag
            data[offset++] = (byte) 0x00;
            System.arraycopy(chalresponse, 0, data, offset, chalresponse.length);
        } else {
            throw new CardException("Wrong challenge-response length (should be 20)");
        }

        APDUCommand plainApdu = new APDUCommand(0xB0, 0x7B, keynbr, 0x00, data);
        return this.cardTransmit(plainApdu);
    }

    /**
     * This function tweak the currently available private stored in the Satochip.
     * Tweaking is based on the 'taproot_tweak_seckey(seckey0, h)' algorithm specification defined here:
     * https://github.com/bitcoin/bips/blob/master/bip-0341.mediawiki#constructing-and-spending-taproot-outputs
     * <p>
     * ins: 0x7C
     * p1: key number or 0xFF for the last derived Bip32 extended key
     * p2: 0x00
     * data: [hash(32b) | option: 2FA-flag(2b)|hmac(20b)]
     * return: [sig]
     */
    public APDUResponse cardTaprootTweakPrivkey(byte keynbr, byte[] tweak) throws CardException {
        byte[] data;
        if(tweak == null) {
            tweak = new byte[32]; // by default use a 32-byte vector filled with '0x00'
        }
        if(tweak.length != 32) {
            throw new CardException("Wrong tweak length (should be 32)");
        }
        data = new byte[33];
        data[0] = (byte) 32;
        System.arraycopy(tweak, 0, data, 1, tweak.length);

        APDUCommand plainApdu = new APDUCommand(0xB0, 0x7C, keynbr, 0x00, data);
        return this.cardTransmit(plainApdu);
    }
}
