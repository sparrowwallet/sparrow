package com.sparrowwallet.sparrow.ur.fountain;

import co.nstant.in.cbor.CborException;
import com.sparrowwallet.drongo.Utils;
import com.sparrowwallet.sparrow.ur.ResultType;
import com.sparrowwallet.sparrow.ur.URTest;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class FountainCodesTest {
    @Test
    public void testRNG3() {
        RandomXoshiro256StarStar rng = new RandomXoshiro256StarStar("Wolf");
        int[] numbers = IntStream.range(0, 100).map(i -> rng.nextInt(1, 10)).toArray();
        int[] expectedNumbers = new int[] {6, 5, 8, 4, 10, 5, 7, 10, 4, 9, 10, 9, 7, 7, 1, 1, 2, 9, 9, 2, 6, 4, 5, 7, 8, 5, 4, 2, 3, 8, 7, 4, 5, 1, 10, 9, 3, 10, 2, 6, 8, 5, 7, 9, 3, 1, 5, 2, 7, 1, 4, 4, 4, 4, 9, 4, 5, 5, 6, 9, 5, 1, 2, 8, 3, 3, 2, 8, 4, 3, 2, 1, 10, 8, 9, 3, 10, 8, 5, 5, 6, 7, 10, 5, 8, 9, 4, 6, 4, 2, 10, 2, 1, 7, 9, 6, 7, 4, 2, 5};

        Assert.assertArrayEquals(expectedNumbers, numbers);
    }

    @Test
    public void testRandomSampler() {
        RandomXoshiro256StarStar rng = new RandomXoshiro256StarStar("Wolf");
        AliasMethod aliasMethod = new AliasMethod(List.of(1d, 2d, 4d, 8d), rng);
        int[] numbers = IntStream.range(0, 500).map(i -> aliasMethod.next()).toArray();
        int[] expectedNumbers = new int[] {2, 1, 2, 0, 2, 2, 0, 1, 0, 1, 1, 2, 1, 2, 3, 3, 1, 2, 0, 1, 0, 1, 0, 0, 0, 1, 1, 1, 3, 0, 1, 0, 2, 0, 1, 3, 3, 0, 3, 1, 2, 1, 2, 2, 0, 3, 2, 3, 2, 3, 1, 3, 1, 1, 0, 0, 1, 0, 0, 3, 0, 0, 2, 1, 2, 3, 3, 3, 3, 2, 0, 2, 3, 0, 0, 3, 1, 0, 2, 1, 1, 3, 0, 0, 2, 1, 1, 3, 3, 1, 3, 0, 1, 1, 0, 1, 0, 0, 1, 0, 3, 2, 2, 2, 1, 1, 0, 1, 3, 1, 0, 3, 3, 1, 3, 2, 1, 2, 2, 1, 3, 3, 3, 3, 2, 0, 0, 2, 2, 0, 2, 2, 1, 3, 2, 1, 2, 2, 2, 3, 0, 2, 1, 3, 1, 3, 1, 3, 0, 2, 2, 3, 2, 3, 1, 1, 1, 2, 3, 0, 1, 2, 3, 1, 2, 2, 1, 3, 3, 3, 2, 1, 0, 1, 1, 3, 2, 2, 3, 0, 0, 2, 0, 1, 0, 2, 2, 2, 1, 0, 2, 1, 2, 1, 3, 0, 0, 1, 0, 0, 0, 0, 1, 2, 1, 0, 1, 3, 1, 1, 3, 2, 1, 0, 2, 2, 0, 1, 1, 3, 0, 3, 3, 0, 1, 3, 3, 1, 2, 1, 1, 1, 2, 3, 2, 2, 1, 3, 2, 1, 3, 0, 2, 2, 0, 1, 3, 3, 0, 1, 1, 2, 3, 0, 2, 3, 1, 2, 1, 0, 2, 2, 0, 1, 2, 1, 3, 3, 0, 0, 3, 1, 2, 2, 0, 0, 2, 1, 1, 3, 2, 0, 3, 0, 0, 3, 0, 0, 3, 2, 2, 3, 3, 3, 3, 3, 3, 2, 1, 2, 2, 0, 2, 3, 3, 1, 3, 2, 1, 3, 2, 0, 0, 0, 0, 1, 0, 3, 1, 1, 1, 0, 0, 3, 0, 1, 1, 3, 2, 3, 3, 3, 2, 2, 2, 2, 0, 2, 2, 0, 2, 3, 2, 0, 3, 2, 2, 3, 3, 0, 3, 0, 2, 1, 2, 1, 0, 2, 2, 0, 0, 0, 0, 3, 0, 3, 0, 2, 3, 0, 3, 3, 3, 3, 3, 2, 3, 1, 2, 1, 0, 3, 0, 0, 1, 3, 0, 0, 0, 1, 3, 0, 3, 1, 0, 3, 2, 3, 0, 0, 1, 1, 3, 1, 3, 3, 1, 1, 2, 3, 3, 0, 0, 0, 2, 2, 2, 2, 1, 0, 2, 2, 3, 2, 2, 0, 2, 3, 2, 0, 3, 0, 1, 2, 2, 0, 2, 3, 0, 0, 2, 0, 3, 0, 1, 1, 3, 2, 2, 2, 0, 1, 2, 3, 3, 2, 2, 1, 2, 3, 1, 1, 1, 0, 3, 3, 0, 1, 2, 1, 0, 3, 2, 0, 3, 1, 1, 2, 2, 0, 1, 3, 0, 1, 3, 0, 0, 2, 0, 3, 0, 1, 2, 2, 0, 3, 1, 2, 0, 2};

        Assert.assertArrayEquals(expectedNumbers, numbers);
    }

    @Test
    public void testShuffle() {
        RandomXoshiro256StarStar rng = new RandomXoshiro256StarStar("Wolf");
        List<Integer> numbers = IntStream.range(1, 11).boxed().collect(Collectors.toList());
        numbers = FountainUtils.shuffled(numbers, rng);
        List<Integer> expectedNumbers = List.of(6, 4, 9, 3, 10, 5, 7, 8, 1, 2);

        Assert.assertEquals(expectedNumbers, numbers);
    }

    @Test
    public void testXOR() {
        RandomXoshiro256StarStar rng = new RandomXoshiro256StarStar("Wolf");
        byte[] data1 = new byte[10];
        rng.nextData(data1);
        Assert.assertEquals("916ec65cf77cadf55cd7", Utils.bytesToHex(data1));

        byte[] data2 = new byte[10];
        rng.nextData(data2);
        Assert.assertEquals("f9cda1a1030026ddd42e", Utils.bytesToHex(data2));

        byte[] data3 = FountainEncoder.xor(data1, data2);
        Assert.assertEquals("68a367fdf47c8b2888f9", Utils.bytesToHex(data3));
    }

    @Test
    public void testEncoderCBOR() {
        byte[] message = URTest.makeMessage(256, "Wolf");
        FountainEncoder encoder = new FountainEncoder(message, 30, 10, 0);
        List<FountainEncoder.Part> parts = IntStream.range(0, 20).mapToObj(i -> encoder.nextPart()).collect(Collectors.toList());
        List<String> partsHex = parts.stream().map(part -> Utils.bytesToHex(part.toCborBytes())).collect(Collectors.toList());
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
                "8514091901001a0167aa07581d2b44620c8371a48f6935d2b525f19c7a4e98e3043a6a64d462870e98ce"
        };

        Assert.assertEquals(Arrays.asList(expectedPartsHex), partsHex);
    }

    @Test
    public void testEncoderIsComplete() {
        byte[] message = URTest.makeMessage(256, "Wolf");
        FountainEncoder encoder = new FountainEncoder(message, 30, 10, 0);
        int generatedPartsCount = 0;
        while(!encoder.isComplete()) {
            encoder.nextPart();
            generatedPartsCount++;
        }

        Assert.assertEquals(encoder.getSeqLen(), generatedPartsCount);
    }

    @Test
    public void testDecoder() {
        String messageSeed = "Wolf";
        int messageSize = 32767;
        int maxFragmentLen = 1000;

        byte[] message = URTest.makeMessage(messageSize, messageSeed);
        FountainEncoder encoder = new FountainEncoder(message, maxFragmentLen, 10, 0);
        FountainDecoder decoder = new FountainDecoder();

        do {
            FountainEncoder.Part part = encoder.nextPart();
            decoder.receivePart(part);
        } while(decoder.getResult() == null);

        Assert.assertEquals(ResultType.SUCCESS, decoder.getResult().type);
        Assert.assertArrayEquals(message, decoder.getResult().data);
    }

    @Test
    public void testDecoderHighFirstSeq() {
        String messageSeed = "Wolf";
        int messageSize = 32767;
        int maxFragmentLen = 1000;

        byte[] message = URTest.makeMessage(messageSize, messageSeed);
        FountainEncoder encoder = new FountainEncoder(message, maxFragmentLen, 10, 100);
        FountainDecoder decoder = new FountainDecoder();

        do {
            FountainEncoder.Part part = encoder.nextPart();
            decoder.receivePart(part);
        } while(decoder.getResult() == null);

        Assert.assertEquals(ResultType.SUCCESS, decoder.getResult().type);
        Assert.assertArrayEquals(message, decoder.getResult().data);
    }

    @Test
    public void testCBOR() throws CborException {
        FountainEncoder.Part part = new FountainEncoder.Part(12, 8, 100, 0x12345678, new byte[] {1,5,3,3,5});
        byte[] cbor = part.toCborBytes();
        FountainEncoder.Part part2 = FountainEncoder.Part.fromCborBytes(cbor);

        Assert.assertArrayEquals(cbor, part2.toCborBytes());
    }
}
