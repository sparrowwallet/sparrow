package com.sparrowwallet.sparrow.ur;

import com.sparrowwallet.drongo.Utils;
import org.junit.Assert;
import org.junit.Test;

public class BytewordsTest {
    @Test
    public void test() {
        byte[] data = Utils.hexToBytes("d9012ca20150c7098580125e2ab0981253468b2dbc5202d8641947da");
        String encoded = Bytewords.encode(data, Bytewords.Style.STANDARD);
        Assert.assertEquals("tuna acid draw oboe acid good slot axis limp lava brag holy door puff monk brag guru frog luau drop roof grim also trip idle chef fuel twin tied draw grim ramp", encoded);
        byte[] data2 = Bytewords.decode(encoded, Bytewords.Style.STANDARD);
        Assert.assertArrayEquals(data, data2);

        encoded = Bytewords.encode(data, Bytewords.Style.URI);
        Assert.assertEquals("tuna-acid-draw-oboe-acid-good-slot-axis-limp-lava-brag-holy-door-puff-monk-brag-guru-frog-luau-drop-roof-grim-also-trip-idle-chef-fuel-twin-tied-draw-grim-ramp", encoded);

        encoded = Bytewords.encode(data, Bytewords.Style.MINIMAL);
        Assert.assertEquals("taaddwoeadgdstaslplabghydrpfmkbggufgludprfgmaotpiecffltntddwgmrp", encoded);
        data2 = Bytewords.decode(encoded, Bytewords.Style.MINIMAL);
        Assert.assertArrayEquals(data, data2);
    }
}
