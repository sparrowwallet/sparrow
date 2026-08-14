package com.sparrowwallet.sparrow.io.keycard;

import com.sparrowwallet.drongo.Utils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;

public class TinyBERTLVTest {
    @Test
    public void testFourByteLengthRejected() {
        //tag 0x80 declaring a ~2GiB body from six bytes of card response
        TinyBERTLV tlv = new TinyBERTLV(Utils.hexToBytes("80847fffffff"));
        Assertions.assertThrows(IllegalArgumentException.class, () -> tlv.readPrimitive(ApplicationInfo.TLV_PUB_KEY));
    }

    @Test
    public void testNegativeLengthRejected() {
        //a four byte length with the sign bit set
        TinyBERTLV tlv = new TinyBERTLV(Utils.hexToBytes("8084ffffffff"));
        Assertions.assertThrows(IllegalArgumentException.class, () -> tlv.readPrimitive(ApplicationInfo.TLV_PUB_KEY));
    }

    @Test
    public void testShortBodyRejectedNotZeroPadded() {
        //five bytes declared, two supplied - previously returned three zero bytes of padding
        TinyBERTLV tlv = new TinyBERTLV(Utils.hexToBytes("80050102"));
        Assertions.assertThrows(IllegalArgumentException.class, () -> tlv.readPrimitive(ApplicationInfo.TLV_PUB_KEY));
    }

    @Test
    public void testLongFormShortBodyRejectedNotZeroPadded() {
        //a long form length of one million, four bytes supplied - previously returned a megabyte of zero padding
        TinyBERTLV tlv = new TinyBERTLV(Utils.hexToBytes("8084000f424001020304"));
        Assertions.assertThrows(IllegalArgumentException.class, () -> tlv.readPrimitive(ApplicationInfo.TLV_PUB_KEY));
    }

    @Test
    public void testMissingLengthByteRejected() {
        TinyBERTLV tlv = new TinyBERTLV(Utils.hexToBytes("80"));
        Assertions.assertThrows(IllegalArgumentException.class, () -> tlv.readPrimitive(ApplicationInfo.TLV_PUB_KEY));
    }

    @Test
    public void testTruncatedLengthHeaderRejected() {
        //0x84 promises four length bytes, only one follows
        TinyBERTLV tlv = new TinyBERTLV(Utils.hexToBytes("808400"));
        Assertions.assertThrows(IllegalArgumentException.class, () -> tlv.readPrimitive(ApplicationInfo.TLV_PUB_KEY));
    }

    @Test
    public void testIndefiniteLengthRejected() {
        TinyBERTLV tlv = new TinyBERTLV(Utils.hexToBytes("8080"));
        Assertions.assertThrows(IllegalArgumentException.class, () -> tlv.readPrimitive(ApplicationInfo.TLV_PUB_KEY));
    }

    @Test
    public void testOversizedConstructedLengthRejected() {
        TinyBERTLV tlv = new TinyBERTLV(Utils.hexToBytes("a4847fffffff"));
        Assertions.assertThrows(IllegalArgumentException.class, () -> tlv.enterConstructed(ApplicationInfo.TLV_APPLICATION_INFO_TEMPLATE));
    }

    @Test
    public void testShortFormLengthStillParses() {
        TinyBERTLV tlv = new TinyBERTLV(Utils.hexToBytes("80020102"));
        Assertions.assertArrayEquals(Utils.hexToBytes("0102"), tlv.readPrimitive(ApplicationInfo.TLV_PUB_KEY));
    }

    @Test
    public void testLongFormLengthStillParses() {
        //a 200 byte body, which requires the 0x81 length form
        byte[] body = new byte[200];
        for(int i = 0; i < body.length; i++) {
            body[i] = (byte)i;
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write(ApplicationInfo.TLV_PUB_KEY);
        TinyBERTLV.writeNum(baos, body.length);
        baos.writeBytes(body);

        TinyBERTLV tlv = new TinyBERTLV(baos.toByteArray());
        Assertions.assertArrayEquals(body, tlv.readPrimitive(ApplicationInfo.TLV_PUB_KEY));
    }

    @Test
    public void testUninitializedCardSelectResponseStillParses() {
        byte[] pubKey = new byte[65];
        pubKey[0] = 0x04;

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        writePrimitive(baos, ApplicationInfo.TLV_PUB_KEY, pubKey);

        ApplicationInfo applicationInfo = new ApplicationInfo(baos.toByteArray());
        Assertions.assertFalse(applicationInfo.isInitializedCard());
        Assertions.assertArrayEquals(pubKey, applicationInfo.getSecureChannelPubKey());
        Assertions.assertTrue(applicationInfo.hasSecureChannelCapability());
    }

    @Test
    public void testInitializedCardSelectResponseStillParses() {
        byte[] instanceUID = new byte[16];
        byte[] pubKey = new byte[65];
        pubKey[0] = 0x04;
        byte[] keyUID = new byte[32];

        ByteArrayOutputStream template = new ByteArrayOutputStream();
        writePrimitive(template, ApplicationInfo.TLV_UID, instanceUID);
        writePrimitive(template, ApplicationInfo.TLV_PUB_KEY, pubKey);
        writePrimitive(template, TinyBERTLV.TLV_INT, new byte[] {0x03, 0x01});
        writePrimitive(template, TinyBERTLV.TLV_INT, new byte[] {0x05});
        writePrimitive(template, ApplicationInfo.TLV_KEY_UID, keyUID);
        writePrimitive(template, ApplicationInfo.TLV_CAPABILITIES, new byte[] {ApplicationInfo.CAPABILITIES_ALL});

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        writePrimitive(baos, ApplicationInfo.TLV_APPLICATION_INFO_TEMPLATE, template.toByteArray());

        ApplicationInfo applicationInfo = new ApplicationInfo(baos.toByteArray());
        Assertions.assertTrue(applicationInfo.isInitializedCard());
        Assertions.assertEquals("3.1", applicationInfo.getAppVersionString());
        Assertions.assertEquals(5, applicationInfo.getFreePairingSlots());
        Assertions.assertArrayEquals(keyUID, applicationInfo.getKeyUID());
        Assertions.assertEquals(ApplicationInfo.CAPABILITIES_ALL, applicationInfo.getCapabilities());
    }

    private void writePrimitive(ByteArrayOutputStream baos, byte tag, byte[] body) {
        baos.write(tag);
        TinyBERTLV.writeNum(baos, body.length);
        baos.writeBytes(body);
    }
}
