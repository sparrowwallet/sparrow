package com.sparrowwallet.sparrow.ur;

import co.nstant.in.cbor.CborBuilder;
import co.nstant.in.cbor.CborEncoder;
import com.sparrowwallet.drongo.Utils;
import com.sparrowwallet.sparrow.ur.fountain.RandomXoshiro256StarStar;
import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class URTest {
    @Test
    public void testSinglePartUR() {
        UR ur = makeMessageUR(50, "Wolf");
        String encoded = UREncoder.encode(ur);
        Assert.assertEquals("ur:bytes/hdeymejtswhhylkepmykhhtsytsnoyoyaxaedsuttydmmhhpktpmsrjtgwdpfnsboxgwlbaawzuefywkdplrsrjynbvygabwjldapfcsdwkbrkch", encoded);
    }

    @Test
    public void testEncode() {
        UR ur = makeMessageUR(256, "Wolf");
        UREncoder urEncoder = new UREncoder(ur, 30, 10, 0);
        List<String> parts = IntStream.range(0, 20).mapToObj(i -> urEncoder.nextPart()).collect(Collectors.toList());
        String[] expectedParts = new String[] {
                "ur:bytes/1-9/ltadascfadaxcywenbpljkhdcahkadaemejtswhhylkepmykhhtsytsnoyoyaxaedsuttydmmhhpktpmsrjtdkgsltgh",
                "ur:bytes/2-9/ltaoascfadaxcywenbpljkhdcagwdpfnsboxgwlbaawzuefywkdplrsrjynbvygabwjldapfcsgmghhkhstlrdcxaefz",
                "ur:bytes/3-9/ltaxascfadaxcywenbpljkhdcahelbknlkuejnbadmssfhfrdpsbiegecpasvssovlgeykssjykklronvsjksopdzool",
                "ur:bytes/4-9/ltaaascfadaxcywenbpljkhdcasotkhemthydawydtaxneurlkosgwcekonertkbrlwmplssjtammdplolsbrdzertas",
                "ur:bytes/5-9/ltahascfadaxcywenbpljkhdcatbbdfmssrkzocwnezmlennjpfzbgmuktrhtejscktelgfpdlrkfyfwdajldejokbwf",
                "ur:bytes/6-9/ltamascfadaxcywenbpljkhdcackjlhkhybssklbwefectpfnbbectrljectpavyrolkzezepkmwidmwoxkilghdsowp",
                "ur:bytes/7-9/ltatascfadaxcywenbpljkhdcavszownjkwtclrtvaynhpahrtoxmwvwatmedibkaegdosftvandiodagdhthtrlnnhy",
                "ur:bytes/8-9/ltayascfadaxcywenbpljkhdcadmsponkkbbhgsolnjntegepmttmoonftnbuoiyrehfrtsabzsttorodklubbuyaetk",
                "ur:bytes/9-9/ltasascfadaxcywenbpljkhdcajskecpmdckihdyhphfotjojtfmlpwmadspaxrkytbztpbauotbgtgtaeaevtgavtny",
                "ur:bytes/10-9/ltbkascfadaxcywenbpljkhdcahkadaemejtswhhylkepmykhhtsytsnoyoyaxaedsuttydmmhhpktpmsrjtwdkiplzs",
                "ur:bytes/11-9/ltbdascfadaxcywenbpljkhdcahelbknlkuejnbadmssfhfrdpsbiegecpasvssovlgeykssjykklronvsjkvetiiapk",
                "ur:bytes/12-9/ltbnascfadaxcywenbpljkhdcarllaluzodmgstospeyiefmwejlwtpedamktksrvlcygmzmmovovllarodtmtbnptrs",
                "ur:bytes/13-9/ltbtascfadaxcywenbpljkhdcamtkgtpknghchchyketwsvwgwfdhpgmgtylctotztpdrpayoschcmhplffziachrfgd",
                "ur:bytes/14-9/ltbaascfadaxcywenbpljkhdcapazmwnvonnvdnsbyleynwtnsjkjndeoldydkbkdslgjkbbkortbelomueekgvstegt",
                "ur:bytes/15-9/ltbsascfadaxcywenbpljkhdcaynmhpddpzoversbdqdfyrehnqzlugmjzmnmtwmrouohtstgsbsahpawkditkckynwt",
                "ur:bytes/16-9/ltbeascfadaxcywenbpljkhdcawygekobamwtlihsnpalpsghenskkiynthdzttsimtojetprsttmukirlrsbtamjtpd",
                "ur:bytes/17-9/ltbyascfadaxcywenbpljkhdcamklgftaxykpewyrtqzhydntpnytyisincxmhtbceaykolduortotiaiaiafhiaoyce",
                "ur:bytes/18-9/ltbgascfadaxcywenbpljkhdcahkadaemejtswhhylkepmykhhtsytsnoyoyaxaedsuttydmmhhpktpmsrjtntwkbkwy",
                "ur:bytes/19-9/ltbwascfadaxcywenbpljkhdcadekicpaajootjzpsdrbalteywllbdsnbinaerkurspbncxgslgftvtsrjtksplcpeo",
                "ur:bytes/20-9/ltbbascfadaxcywenbpljkhdcayapmrleeleaxpasfrtrdkncffwjyjzgyetdmlewtkpktgllepfrltatazcksmhkbot"
        };
        Assert.assertArrayEquals("", expectedParts, parts.toArray());
    }

    @Test
    public void testMultipartUR() {
        UR ur = makeMessageUR(32767, "Wolf");
        int maxFragmentLen = 1000;
        UREncoder urEncoder = new UREncoder(ur, maxFragmentLen, 10, 100);
        URDecoder urDecoder = new URDecoder();

        do {
            String part = urEncoder.nextPart();
            urDecoder.receivePart(part);
        } while(urDecoder.getResult() == null);

        Assert.assertEquals(ResultType.SUCCESS, urDecoder.getResult().type);

        UR decodedUR = urDecoder.getResult().ur;
        Assert.assertEquals(ur, decodedUR);
    }

    public static byte[] makeMessage(int len, String seed) {
        RandomXoshiro256StarStar rng = new RandomXoshiro256StarStar(seed);
        byte[] message = new byte[len];
        rng.nextData(message);
        return message;
    }

    private UR makeMessageUR(int len, String seed) {
        try {
            byte[] message = makeMessage(len, seed);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            new CborEncoder(baos).encode(new CborBuilder()
                    .add(message)
                    .build());
            byte[] cbor = baos.toByteArray();
            return new UR("bytes", cbor);
        } catch(Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testCbor() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        new CborEncoder(baos).encode(new CborBuilder()
                .add(Utils.hexToBytes("00112233445566778899aabbccddeeff"))
                .build());

        byte[] cbor = baos.toByteArray();
        Assert.assertEquals("5000112233445566778899aabbccddeeff", Utils.bytesToHex(cbor));

        UR ur = new UR("bytes", cbor);
        String encoded = UREncoder.encode(ur);
        Assert.assertEquals("ur:bytes/gdaebycpeofygoiyktlonlpkrksfutwyzowmfyeozs", encoded);
    }
}
