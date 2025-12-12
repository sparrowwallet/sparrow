package com.sparrowwallet.sparrow.io.keycard;

import com.sparrowwallet.drongo.crypto.ECDSASignature;
import com.sparrowwallet.drongo.crypto.ECKey;
import com.sparrowwallet.drongo.protocol.Sha256Hash;

import java.math.BigInteger;
import java.util.Arrays;

/**
 * Signature with recoverable public key.
 */
public class RecoverableSignature {
    private byte[] publicKey;
    private int recId;
    private byte[] r;
    private byte[] s;
    private boolean compressed;

    public static final byte TLV_SIGNATURE_TEMPLATE = (byte) 0xA0;
    public static final byte TLV_RAW_SIGNATURE = (byte) 0x80;
    public static final byte TLV_ECDSA_TEMPLATE = (byte) 0x30;

    /**
     * Parses a signature from the card and calculates the recovery ID.
     *
     * @param hash    the message being signed
     * @param tlvData the signature as returned from the card
     */
    public RecoverableSignature(byte[] hash, byte[] tlvData) {
        TinyBERTLV tlv = new TinyBERTLV(tlvData);
        int tag = tlv.readTag();
        tlv.unreadLastTag();

        if(tag == TLV_RAW_SIGNATURE) {
            initFromRawSignature(hash, tlv.readPrimitive(tag));
        } else if(tag == TLV_SIGNATURE_TEMPLATE) {
            initFromLegacy(hash, tlv);
        } else {
            throw new IllegalArgumentException("invalid tlv");
        }
    }

    private void initFromLegacy(byte[] hash, TinyBERTLV tlv) {
        tlv.enterConstructed(TLV_SIGNATURE_TEMPLATE);
        this.publicKey = tlv.readPrimitive(ApplicationInfo.TLV_PUB_KEY);
        tlv.enterConstructed(TLV_ECDSA_TEMPLATE);
        this.r = toUInt(tlv.readPrimitive(TinyBERTLV.TLV_INT));
        this.s = toUInt(tlv.readPrimitive(TinyBERTLV.TLV_INT));
        this.compressed = false;

        calculateRecID(hash);
    }

    private void initFromRawSignature(byte[] hash, byte[] signature) {
        this.r = Arrays.copyOfRange(signature, 0, 32);
        this.s = Arrays.copyOfRange(signature, 32, 64);
        this.recId = signature[64];
        this.compressed = false;
        this.publicKey = recoverFromSignature(this.recId, hash, this.r, this.s, this.compressed);
    }

    public RecoverableSignature(byte[] publicKey, boolean compressed, byte[] r, byte[] s, int recId) {
        this.publicKey = publicKey;
        this.r = r;
        this.s = s;
        this.compressed = compressed;
        this.recId = recId;
    }

    void calculateRecID(byte[] hash) {
        recId = -1;

        for(int i = 0; i < 4; i++) {
            byte[] candidate = recoverFromSignature(i, hash, r, s, compressed);

            if(Arrays.equals(candidate, publicKey)) {
                recId = i;
                break;
            }
        }

        if(recId == -1) {
            throw new IllegalArgumentException("Unrecoverable signature, cannot find recId");
        }
    }

    static byte[] toUInt(byte[] signedInt) {
        if(signedInt[0] == 0) {
            return Arrays.copyOfRange(signedInt, 1, signedInt.length);
        } else {
            return signedInt;
        }
    }

    /**
     * The public key associated to this signature.
     *
     * @return the public key associated to this signature
     */
    public byte[] getPublicKey() {
        return publicKey;
    }

    /**
     * The recovery ID
     *
     * @return recovery ID
     */
    public int getRecId() {
        return recId;
    }

    /**
     * The R value.
     *
     * @return r
     */
    public byte[] getR() {
        return r;
    }

    /**
     * The S value
     *
     * @return s
     */
    public byte[] getS() {
        return s;
    }

    static byte[] recoverFromSignature(int recId, byte[] hash, byte[] r, byte[] s, boolean compressed) {
        ECDSASignature sig = new ECDSASignature(new BigInteger(1, r), new BigInteger(1, s));
        ECKey key = ECKey.recoverFromSignature(recId, sig, Sha256Hash.wrap(hash), compressed);

        if(key == null) {
            return null;
        }

        return key.getPubKey();
    }
}
