package com.sparrowwallet.sparrow.io.keycard;

import org.bouncycastle.asn1.x9.X9ECParameters;
import org.bouncycastle.asn1.x9.X9IntegerConverter;
import org.bouncycastle.crypto.ec.CustomNamedCurves;
import org.bouncycastle.crypto.params.ECDomainParameters;
import org.bouncycastle.math.ec.ECAlgorithms;
import org.bouncycastle.math.ec.ECPoint;
import org.bouncycastle.math.ec.FixedPointUtil;
import org.bouncycastle.math.ec.custom.sec.SecP256K1Curve;

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

  private static final X9ECParameters CURVE_PARAMS = CustomNamedCurves.getByName("secp256k1");
  static final ECDomainParameters CURVE;

  static {
    FixedPointUtil.precompute(CURVE_PARAMS.getG());
    CURVE = new ECDomainParameters(CURVE_PARAMS.getCurve(), CURVE_PARAMS.getG(), CURVE_PARAMS.getN(), CURVE_PARAMS.getH());
  }

  /**
   * Parses a signature from the card and calculates the recovery ID.
   *
   * @param hash the message being signed
   * @param tlvData the signature as returned from the card
   */
  public RecoverableSignature(byte[] hash, byte[] tlvData) {
    TinyBERTLV tlv = new TinyBERTLV(tlvData);
    int tag = tlv.readTag();
    tlv.unreadLastTag();

    if (tag == TLV_RAW_SIGNATURE) {
      initFromRawSignature(hash, tlv.readPrimitive(tag));
    } else if (tag == TLV_SIGNATURE_TEMPLATE) {
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

    for (int i = 0; i < 4; i++) {
      byte[] candidate = recoverFromSignature(i, hash, r, s, compressed);

      if (Arrays.equals(candidate, publicKey)) {
        recId = i;
        break;
      }
    }

    if (recId == -1) {
      throw new IllegalArgumentException("Unrecoverable signature, cannot find recId");
    }
  }

  static byte[] toUInt(byte[] signedInt) {
    if (signedInt[0] == 0) {
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
   * @return s
   */
  public byte[] getS() {
    return s;
  }

  static byte[] recoverFromSignature(int recId, byte[] hash, byte[] r, byte[] s, boolean compressed) {
    BigInteger h = new BigInteger(1, hash);
    BigInteger br = new BigInteger(1, r);
    BigInteger bs = new BigInteger(1, s);

    return recoverFromSignature(recId, h, br, bs, compressed);
  }

  static byte[] recoverFromSignature(int recId, BigInteger e, BigInteger r, BigInteger s, boolean compressed) {
    BigInteger n = CURVE.getN();
    BigInteger i = BigInteger.valueOf((long) recId / 2);
    BigInteger x = r.add(i.multiply(n));
    BigInteger prime = SecP256K1Curve.q;

    if (x.compareTo(prime) >= 0) {
      return null;
    }

    ECPoint R = decompressKey(x, (recId & 1) == 1);

    if (!R.multiply(n).isInfinity()) {
      return null;
    }

    BigInteger eInv = BigInteger.ZERO.subtract(e).mod(n);
    BigInteger rInv = r.modInverse(n);
    BigInteger srInv = rInv.multiply(s).mod(n);
    BigInteger eInvrInv = rInv.multiply(eInv).mod(n);
    ECPoint q = ECAlgorithms.sumOfTwoMultiplies(CURVE.getG(), eInvrInv, R, srInv);
    return q.getEncoded(compressed);
  }

  private static ECPoint decompressKey(BigInteger xBN, boolean yBit) {
    X9IntegerConverter x9 = new X9IntegerConverter();
    byte[] compEnc = x9.integerToBytes(xBN, 1 + x9.getByteLength(CURVE.getCurve()));
    compEnc[0] = (byte)(yBit ? 0x03 : 0x02);
    return CURVE.getCurve().decodePoint(compEnc);
  }
}
