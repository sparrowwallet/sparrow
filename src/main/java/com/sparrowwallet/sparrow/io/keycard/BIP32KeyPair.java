package com.sparrowwallet.sparrow.io.keycard;

import org.bouncycastle.math.ec.ECPoint;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigInteger;
import java.util.Arrays;

/**
 * Represents a BIP32 keypair. This can be a master key or any other key in the path. Contains convenience method to
 * read and write formats the the card understands.
 */
public class BIP32KeyPair {
  private byte[] privateKey;
  private byte[] chainCode;
  private byte[] publicKey;

  static final byte TLV_KEY_TEMPLATE = (byte) 0xA1;
  static final byte TLV_PUB_KEY = (byte) 0x80;
  static final byte TLV_PRIV_KEY = (byte) 0x81;
  static final byte TLV_CHAIN_CODE = (byte) 0x82;

  /**
   * Returns a BIP32 keypair from a BIP32 binary seed. It includes all components.
   *
   * @param binarySeed the binary seed
   * @return the BIP32 keypair
   */
  public static BIP32KeyPair fromBinarySeed(byte[] binarySeed) {
    try {
      Mac hmacSHA512 = Mac.getInstance("HmacSHA512");
      SecretKeySpec keySpec = new SecretKeySpec("Bitcoin seed".getBytes(), "HmacSHA512");
      hmacSHA512.init(keySpec);
      byte[] mac = hmacSHA512.doFinal(binarySeed);
      return new BIP32KeyPair(Arrays.copyOf(mac, 32), Arrays.copyOfRange(mac, 32, 64), null);
    } catch (Exception e) {
      throw new RuntimeException("Is BouncyCastle correctly installed? ", e);
    }
  }

  /**
   * Constructs a BIP32 keypair from a KEY TEMPLATE TLV. Data can be the output of the EXPORT KEY command.
   *
   * @param tlvData the TLV data
   * @return the BIP32 keypair
   */
  public static BIP32KeyPair fromTLV(byte[] tlvData) {
    TinyBERTLV tlv = new TinyBERTLV(tlvData);
    tlv.enterConstructed(TLV_KEY_TEMPLATE);

    byte[] pubKey = null;
    byte[] privKey = null;
    byte[] chainCode = null;

    int tag = tlv.readTag();

    if (tag == TLV_PUB_KEY) {
      tlv.unreadLastTag();
      pubKey = tlv.readPrimitive(TLV_PUB_KEY);
      tag = tlv.readTag();
    }

    if (tag == TLV_PRIV_KEY) {
      tlv.unreadLastTag();
      privKey = tlv.readPrimitive(TLV_PRIV_KEY);
      tag = tlv.readTag();
    }

    if (tag == TLV_CHAIN_CODE) {
      tlv.unreadLastTag();
      chainCode = tlv.readPrimitive(TLV_CHAIN_CODE);
    }    

    return new BIP32KeyPair(privKey, chainCode, pubKey);
  }

  /**
   * Low level constructor. If the private key is not null, the public key can be omitted and it will be calculated
   * automatically.
   *
   * @param privateKey the private key
   * @param chainCode the chain code
   * @param publicKey the public key
   */
  public BIP32KeyPair(byte[] privateKey, byte[] chainCode, byte[] publicKey) {    
    this.privateKey = privateKey;
    this.chainCode = chainCode;
    
    if (publicKey != null) {
      this.publicKey = publicKey;
    } else {
      calculatePublicKey();
    }
  }

  private void calculatePublicKey() {
    BigInteger k = new BigInteger(1, this.privateKey);
    ECPoint pubKey = RecoverableSignature.CURVE.getG().multiply(k);
    this.publicKey = pubKey.getEncoded(false);
  }

  /**
   * Returns the TLV representation of this object.
   *
   * @return the TLV representation of this object.
   */
  public byte[] toTLV() {
    return toTLV(true);
  }

  /**
   * Returns the TLV representation of this object, optionally omitting the public component.
   *
   * @return the TLV representation of this object.
   */
  public byte[] toTLV(boolean includePublic) {
    int privLen = privateKey.length;
    int privOff = 0;

    if(privateKey[0] == 0x00) {
      privOff++;
      privLen--;
    }

    int off = 0;
    int totalLength = includePublic ? (publicKey.length + 2) : 0;
    totalLength += (privLen + 2);
    totalLength += isExtended() ? (chainCode.length + 2) : 0;

    if (totalLength > 127) {
      totalLength += 3;
    } else {
      totalLength += 2;
    }

    byte[] data = new byte[totalLength];
    data[off++] = TLV_KEY_TEMPLATE;

    if (totalLength > 127) {
      data[off++] = (byte) 0x81;
      data[off++] = (byte) (totalLength - 3);
    } else {
      data[off++] = (byte) (totalLength - 2);
    }

    if (includePublic) {
      data[off++] = TLV_PUB_KEY;
      data[off++] = (byte) publicKey.length;
      System.arraycopy(publicKey, 0, data, off, publicKey.length);
      off += publicKey.length;
    }

    data[off++] = TLV_PRIV_KEY;
    data[off++] = (byte) privLen;
    System.arraycopy(privateKey, privOff, data, off, privLen);
    off += privLen;

    if (isExtended()) {
      data[off++] = (byte) TLV_CHAIN_CODE;
      data[off++] = (byte) chainCode.length;
      System.arraycopy(chainCode, 0, data, off, chainCode.length);
    }

    return data;
  }

  /**
   * Returns the private key. Might be null.
   *
   * @return the private key
   */
  public byte[] getPrivateKey() {
    return privateKey;
  }

  /**
   * Returns the chain code. Might be null.
   *
   * @return the chain code
   */
  public byte[] getChainCode() {
    return chainCode;
  }

  /**
   * Returns the public key. Is never null.
   * @return the public key
   */
  public byte[] getPublicKey() {
    return publicKey;
  }

  /**
   * True if only the public key is contained, false otherwise.
   * @return true or false
   */
  public boolean isPublicOnly() {
    return privateKey == null;
  }

  /**
   * True if the chain code is contained, false otherwise.
   * @return true or false
   */
  public boolean isExtended() {
    return chainCode != null;
  }
}
