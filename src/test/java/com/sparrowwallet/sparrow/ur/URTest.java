package com.sparrowwallet.sparrow.ur;

import co.nstant.in.cbor.CborBuilder;
import co.nstant.in.cbor.CborEncoder;
import com.sparrowwallet.drongo.Utils;
import com.sparrowwallet.sparrow.ur.fountain.FountainEncoder;
import com.sparrowwallet.sparrow.ur.fountain.RandomXoshiro256StarStar;
import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
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
                "ur:bytes/10-9/ltbkascfadaxcywenbpljkhdcazoqdayfeaavsnnrffhjnfytplguytsoyspgdrhluheihtyettewtytcfrtdeahhdad",
                "ur:bytes/11-9/ltbdascfadaxcywenbpljkhdcavdintbiyjltafyknfspefrvdondtvlgdckfslthkgtghsbsbbtiyechthdlakobtfd",
                "ur:bytes/12-9/ltbnascfadaxcywenbpljkhdcalndikttpecueksoecypdssvtplkiryjydioefywyrtjlsedppagwpseturfhbzmdmd",
                "ur:bytes/13-9/ltbtascfadaxcywenbpljkhdcazoqdayfeaavsnnrffhjnfytplguytsoyspgdrhluheihtyettewtytcfrtutwtoyon",
                "ur:bytes/14-9/ltbaascfadaxcywenbpljkhdcanbsfjpsotnltmhmoztgmlbykfgrsntlsserojoisbzmhbegspkjyhhwnqdfneobkfd",
                "ur:bytes/15-9/ltbsascfadaxcywenbpljkhdcaynmhpddpzoversbdqdfyrehnqzlugmjzmnmtwmrouohtstgsbsahpawkditkckynwt",
                "ur:bytes/16-9/ltbeascfadaxcywenbpljkhdcazoqdayfeaavsnnrffhjnfytplguytsoyspgdrhluheihtyettewtytcfrtghtduycm",
                "ur:bytes/17-9/ltbyascfadaxcywenbpljkhdcarpfeneknvyyadifltalghskpgrfgsngulagspfpthyrpgrsoatjnuyflvsdmpyinmw",
                "ur:bytes/18-9/ltbgascfadaxcywenbpljkhdcavtrfmwktecjnnsyafsemaaspglynhhrhmyjyoelgjtpyhkssamdsfehfnsfrcfrnyk",
                "ur:bytes/19-9/ltbwascfadaxcywenbpljkhdcaenbgkghtlbiybwfpjlbyecmoythnmesbkopahtiofywnutvacfhdjyiobwrtlbtbme",
                "ur:bytes/20-9/ltbbascfadaxcywenbpljkhdcarssrwyztwmaemotbytayfhvwltmocmndlpnsjejtdkhyntpflboevtrnwsdkjssbrs"
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

    @Test
    public void testEncoderCBOR() {
        byte[] message = makeMessage(256, "Wolf");
        FountainEncoder fountainEncoder = new FountainEncoder(message, 30, 10, 0);
        FountainEncoder.Part[] parts = IntStream.range(0, 20).mapToObj(i -> fountainEncoder.nextPart()).collect(Collectors.toList()).toArray(new FountainEncoder.Part[20]);
        List<String> partsHex = Arrays.stream(parts).map(part -> Utils.bytesToHex(part.toCborBytes())).collect(Collectors.toList());
        String[] expectedPartsHex = new String[] {
                "8501091901001a0167aa07581d916ec65cf77cadf55cd7f9cda1a1030026ddd42e905b77adc36e4f2d3c",
                "8502091901001a0167aa07581dcba44f7f04f2de44f42d84c374a0e149136f25b01852545961d55f7f7a",
                "8503091901001a0167aa07581d8cde6d0e2ec43f3b2dcb644a2209e8c9e34af5c4747984a5e873c9cf5f",
                "8504091901001a0167aa07581d965e25ee29039fdf8ca74f1c769fc07eb7ebaec46e0695aea6cbd60b3e",
                "8505091901001a0167aa07581dc4bbff1b9ffe8a9e7240129377b9d3711ed38d412fbb4442256f1e6f59",
                "8506091901001a0167aa07581d5e0fc57fed451fb0a0101fb76b1fb1e1b88cfdfdaa946294a47de8fff1",
                "8507091901001a0167aa07581d73f021c0e6f65b05c0a494e50791270a0050a73ae69b6725505a2ec8a5",
                "8508091901001a0167aa07581d791457c9876dd34aadd192a53aa0dc66b556c0c215c7ceb8248b717c22",
                "8509091901001a0167aa07581d951e65305b56a3706e3e86eb01c803bbf915d80edcd64d4d0000000000",
                "850a091901001a0167aa07581d4a1b58fa2733399e5ee04d87a2d1628186e3cd250f3ae0e25d7ae7a22b",
                "850b091901001a0167aa07581dd35acd70953cf29b542a94cbd75790c73cb4cb1056d56557bf0b70b936",
                "850c091901001a0167aa07581d8cde6d0e2ec43f3b2dcb644a2209e8c9e34af5c4747984a5e873c9cf5f",
                "850d091901001a0167aa07581d760be7ad1c6187902bbc04f539b9ee5eb8ea6833222edea36031306c01",
                "850e091901001a0167aa07581dcba44f7f04f2de44f42d84c374a0e149136f25b01852545961d55f7f7a",
                "850f091901001a0167aa07581d262518878e747c6eee337fbbd189f77b385efe55597b54cab65b7f8ac0",
                "8510091901001a0167aa07581d2d4a0b8fb95226315ab796cd72f9c5f8ea2a5a84221f7e31318f71b7df",
                "8511091901001a0167aa07581d81bf178523c1e7daaacdc944d67183c8958ce8951768b4bb3cafb8dd51",
                "8512091901001a0167aa07581d8e1548d10a2cd18416a3428bcb1fe2e3cac640274e91df20bdbd4e4df9",
                "8513091901001a0167aa07581d8a65ebbda606df01244a3dad6b76e258150e4c07021ce5c07ed30160c5",
                "8514091901001a0167aa07581d2b44620c8371a48f6935d2b525f19c7a4e98e3043a6a64d462870e98ce"};

        Assert.assertEquals(Arrays.asList(expectedPartsHex), partsHex);
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
