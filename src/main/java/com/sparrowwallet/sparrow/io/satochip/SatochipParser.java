package com.sparrowwallet.sparrow.io.satochip;

import com.sparrowwallet.drongo.protocol.Sha256Hash;
import com.sparrowwallet.drongo.crypto.ECKey;
import com.sparrowwallet.drongo.crypto.ECDSASignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.smartcardio.CardException;
import java.util.Arrays;

public class SatochipParser {
    private static final Logger log = LoggerFactory.getLogger(SatochipParser.class);

    public SatochipParser() {
    }

    /****************************************
     *                  PARSER                *
     ****************************************/

    public byte[] parseInitiateSecureChannel(APDUResponse rapdu) throws CardException {
        try {
            byte[] data = rapdu.getData();

            // data= [coordxSize | coordx | sig1Size | sig1 |  sig2Size | sig2]
            int offset = 0;
            int coordxSize = 256 * data[offset++] + data[offset++];

            byte[] coordx = new byte[coordxSize];
            System.arraycopy(data, offset, coordx, 0, coordxSize);
            offset += coordxSize;

            // msg1 is [coordx_size | coordx]
            byte[] msg1 = new byte[2 + coordxSize];
            System.arraycopy(data, 0, msg1, 0, msg1.length);

            int sig1Size = 256 * data[offset++] + data[offset++];
            byte[] sig1 = new byte[sig1Size];
            System.arraycopy(data, offset, sig1, 0, sig1Size);
            offset += sig1Size;

            // msg2 is [coordxSize | coordx | sig1Size | sig1]
            byte[] msg2 = new byte[2 + coordxSize + 2 + sig1Size];
            System.arraycopy(data, 0, msg2, 0, msg2.length);

            int sig2Size = 256 * data[offset++] + data[offset++];
            byte[] sig2 = new byte[sig2Size];
            System.arraycopy(data, offset, sig2, 0, sig2Size);
            offset += sig2Size;

            return recoverPubkey(msg1, sig1, coordx, false);
        } catch(Exception e) {
            throw new CardException("Error parsing Satochip response", e);
        }
    }

    public byte[][] parseBip32GetExtendedKey(APDUResponse rapdu) throws CardException {
        try {
            byte[][] extendedkey = new byte[2][];
            extendedkey[0] = new byte[33]; // pubkey
            extendedkey[1] = new byte[32]; // chaincode

            byte[] data = rapdu.getData();
            //data: [chaincode(32b) | coordx_size(2b) | coordx | sig_size(2b) | sig | sig_size(2b) | sig2]

            int offset = 0;
            byte[] chaincode = new byte[32];
            System.arraycopy(data, offset, chaincode, 0, chaincode.length);
            offset += 32;

            int coordxSize = 256 * (data[offset++] & 0x7f) + data[offset++]; // (data[32] & 0x80) is ignored (optimization flag)
            byte[] coordx = new byte[coordxSize];
            System.arraycopy(data, offset, coordx, 0, coordxSize);
            offset += coordxSize;

            // msg1 is [chaincode | coordx_size | coordx]
            byte[] msg1 = new byte[32 + 2 + coordxSize];
            System.arraycopy(data, 0, msg1, 0, msg1.length);

            int sig1Size = 256 * data[offset++] + data[offset++];
            byte[] sig1 = new byte[sig1Size];
            System.arraycopy(data, offset, sig1, 0, sig1Size);
            offset += sig1Size;

            // msg2 is [chaincode | coordxSize | coordx | sig1Size | sig1]
            byte[] msg2 = new byte[32 + 2 + coordxSize + 2 + sig1Size];
            System.arraycopy(data, 0, msg2, 0, msg2.length);

            int sig2Size = 256 * data[offset++] + data[offset++];
            byte[] sig2 = new byte[sig2Size];
            System.arraycopy(data, offset, sig2, 0, sig2Size);
            offset += sig2Size;

            byte[] pubkey = recoverPubkey(msg1, sig1, coordx, true); // true: compressed (33 bytes)

            // todo: recover from si2
            System.arraycopy(pubkey, 0, extendedkey[0], 0, pubkey.length);
            System.arraycopy(chaincode, 0, extendedkey[1], 0, chaincode.length);
            return extendedkey;
        } catch(Exception e) {
            throw new CardException("Error parsing Satochip extended key", e);
        }
    }

    /****************************************
     *             recovery  methods        *
     ****************************************/

    public byte[] recoverPubkey(byte[] msg, byte[] dersig, byte[] coordx, Boolean compressed) throws CardException {
        // convert msg to hash
        //byte[] hash = Sha256Hash.hash(msg);
        ECDSASignature ecdsaSig = ECDSASignature.decodeFromDER(dersig);

        byte recId = -1;
        ECKey k = null;
        for(byte i = 0; i < 4; i++) {
            k = ECKey.recoverFromSignature(i, ecdsaSig, Sha256Hash.of(msg), compressed);
            if(k != null && Arrays.equals(k.getPubKeyXCoord(), coordx)) {
                recId = i;
                break;
            }
        }
        if(recId == -1) {
            throw new CardException("Could not construct a recoverable key. This should never happen.");
        }

        return k.getPubKey();
    }
}