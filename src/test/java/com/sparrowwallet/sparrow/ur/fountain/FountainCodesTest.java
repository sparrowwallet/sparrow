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
import java.util.zip.CRC32;

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
        RandomSampler randomSampler = new RandomSampler(List.of(1d, 2d, 4d, 8d));
        int[] numbers = IntStream.range(0, 500).map(i -> randomSampler.next(rng)).toArray();
        int[] expectedNumbers = new int[] {3, 3, 3, 3, 3, 3, 3, 0, 2, 3, 3, 3, 3, 1, 2, 2, 1, 3, 3, 2, 3, 3, 1, 1, 2, 1, 1, 3, 1, 3, 1, 2, 0, 2, 1, 0, 3, 3, 3, 1, 3, 3, 3, 3, 1, 3, 2, 3, 2, 2, 3, 3, 3, 3, 2, 3, 3, 0, 3, 3, 3, 3, 1, 2, 3, 3, 2, 2, 2, 1, 2, 2, 1, 2, 3, 1, 3, 0, 3, 2, 3, 3, 3, 3, 3, 3, 3, 3, 2, 3, 1, 3, 3, 2, 0, 2, 2, 3, 1, 1, 2, 3, 2, 3, 3, 3, 3, 2, 3, 3, 3, 3, 3, 2, 3, 1, 2, 1, 1, 3, 1, 3, 2, 2, 3, 3, 3, 1, 3, 3, 3, 3, 3, 3, 3, 3, 2, 3, 2, 3, 3, 1, 2, 3, 3, 1, 3, 2, 3, 3, 3, 2, 3, 1, 3, 0, 3, 2, 1, 1, 3, 1, 3, 2, 3, 3, 3, 3, 2, 0, 3, 3, 1, 3, 0, 2, 1, 3, 3, 1, 1, 3, 1, 2, 3, 3, 3, 0, 2, 3, 2, 0, 1, 3, 3, 3, 2, 2, 2, 3, 3, 3, 3, 3, 2, 3, 3, 3, 3, 2, 3, 3, 2, 0, 2, 3, 3, 3, 3, 2, 1, 1, 1, 2, 1, 3, 3, 3, 2, 2, 3, 3, 1, 2, 3, 0, 3, 2, 3, 3, 3, 3, 0, 2, 2, 3, 2, 2, 3, 3, 3, 3, 1, 3, 2, 3, 3, 3, 3, 3, 2, 2, 3, 1, 3, 0, 2, 1, 3, 3, 3, 3, 3, 3, 3, 3, 1, 3, 3, 3, 3, 2, 2, 2, 3, 1, 1, 3, 2, 2, 0, 3, 2, 1, 2, 1, 0, 3, 3, 3, 2, 2, 3, 2, 1, 2, 0, 0, 3, 3, 2, 3, 3, 2, 3, 3, 3, 3, 3, 2, 2, 2, 3, 3, 3, 3, 3, 1, 1, 3, 2, 2, 3, 1, 1, 0, 1, 3, 2, 3, 3, 2, 3, 3, 2, 3, 3, 2, 2, 2, 2, 3, 2, 2, 2, 2, 2, 1, 2, 3, 3, 2, 2, 2, 2, 3, 3, 2, 0, 2, 1, 3, 3, 3, 3, 0, 3, 3, 3, 3, 2, 2, 3, 1, 3, 3, 3, 2, 3, 3, 3, 2, 3, 3, 3, 3, 2, 3, 2, 1, 3, 3, 3, 3, 2, 2, 0, 1, 2, 3, 2, 0, 3, 3, 3, 3, 3, 3, 1, 3, 3, 2, 3, 2, 2, 3, 3, 3, 3, 3, 2, 2, 3, 3, 2, 2, 2, 1, 3, 3, 3, 3, 1, 2, 3, 2, 3, 3, 2, 3, 2, 3, 3, 3, 2, 3, 1, 2, 3, 2, 1, 1, 3, 3, 2, 3, 3, 2, 3, 3, 0, 0, 1, 3, 3, 2, 3, 3, 3, 3, 1, 3, 3, 0, 3, 2, 3, 3, 1, 3, 3, 3, 3, 3, 3, 3, 0, 3, 3, 2};

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
    public void testPartitionAndJoin() {
        byte[] message = URTest.makeMessage(1024, "Wolf");
        int fragmentLen = FountainEncoder.findNominalFragmentLength(message.length, 10, 100);
        List<byte[]> fragments = FountainEncoder.partitionMessage(message, fragmentLen);
        List<String> fragmentsHex = fragments.stream().map(Utils::bytesToHex).collect(Collectors.toList());
        String[] expectedFragmentsHex = new String[] {
                "916ec65cf77cadf55cd7f9cda1a1030026ddd42e905b77adc36e4f2d3ccba44f7f04f2de44f42d84c374a0e149136f25b01852545961d55f7f7a8cde6d0e2ec43f3b2dcb644a2209e8c9e34af5c4747984a5e873c9cf5f965e25ee29039f",
                "df8ca74f1c769fc07eb7ebaec46e0695aea6cbd60b3ec4bbff1b9ffe8a9e7240129377b9d3711ed38d412fbb4442256f1e6f595e0fc57fed451fb0a0101fb76b1fb1e1b88cfdfdaa946294a47de8fff173f021c0e6f65b05c0a494e50791",
                "270a0050a73ae69b6725505a2ec8a5791457c9876dd34aadd192a53aa0dc66b556c0c215c7ceb8248b717c22951e65305b56a3706e3e86eb01c803bbf915d80edcd64d4d41977fa6f78dc07eecd072aae5bc8a852397e06034dba6a0b570",
                "797c3a89b16673c94838d884923b8186ee2db5c98407cab15e13678d072b43e406ad49477c2e45e85e52ca82a94f6df7bbbe7afbed3a3a830029f29090f25217e48d1f42993a640a67916aa7480177354cc7440215ae41e4d02eae9a1912",
                "33a6d4922a792c1b7244aa879fefdb4628dc8b0923568869a983b8c661ffab9b2ed2c149e38d41fba090b94155adbed32f8b18142ff0d7de4eeef2b04adf26f2456b46775c6c20b37602df7da179e2332feba8329bbb8d727a138b4ba7a5",
                "03215eda2ef1e953d89383a382c11d3f2cad37a4ee59a91236a3e56dcf89f6ac81dd4159989c317bd649d9cbc617f73fe10033bd288c60977481a09b343d3f676070e67da757b86de27bfca74392bac2996f7822a7d8f71a489ec6180390",
                "089ea80a8fcd6526413ec6c9a339115f111d78ef21d456660aa85f790910ffa2dc58d6a5b93705caef1091474938bd312427021ad1eeafbd19e0d916ddb111fabd8dcab5ad6a6ec3a9c6973809580cb2c164e26686b5b98cfb017a337968",
                "c7daaa14ae5152a067277b1b3902677d979f8e39cc2aafb3bc06fcf69160a853e6869dcc09a11b5009f91e6b89e5b927ab1527a735660faa6012b420dd926d940d742be6a64fb01cdc0cff9faa323f02ba41436871a0eab851e7f5782d10",
                "fbefde2a7e9ae9dc1e5c2c48f74f6c824ce9ef3c89f68800d44587bedc4ab417cfb3e7447d90e1e417e6e05d30e87239d3a5d1d45993d4461e60a0192831640aa32dedde185a371ded2ae15f8a93dba8809482ce49225daadfbb0fec629e",
                "23880789bdf9ed73be57fa84d555134630e8d0f7df48349f29869a477c13ccca9cd555ac42ad7f568416c3d61959d0ed568b2b81c7771e9088ad7fd55fd4386bafbf5a528c30f107139249357368ffa980de2c76ddd9ce4191376be0e6b5",
                "170010067e2e75ebe2d2904aeb1f89d5dc98cd4a6f2faaa8be6d03354c990fd895a97feb54668473e9d942bb99e196d897e8f1b01625cf48a7b78d249bb4985c065aa8cd1402ed2ba1b6f908f63dcd84b66425df00000000000000000000"
        };

        Assert.assertEquals(Arrays.asList(expectedFragmentsHex), fragmentsHex);

        byte[] rejoinedMessage = FountainDecoder.joinFragments(fragments, message.length);
        Assert.assertArrayEquals(message, rejoinedMessage);
    }

    @Test
    public void testChooseDegree() {
        byte[] message = URTest.makeMessage(1024, "Wolf");
        int fragmentLen = FountainEncoder.findNominalFragmentLength(message.length, 10, 100);
        List<byte[]> fragments = FountainEncoder.partitionMessage(message, fragmentLen);
        List<Integer> degrees = IntStream.rangeClosed(1, 200).mapToObj( nonce -> {
            RandomXoshiro256StarStar partRng = new RandomXoshiro256StarStar("Wolf-" + nonce);
            return FountainUtils.chooseDegree(fragments.size(), partRng);
        }).collect(Collectors.toList());
        Integer[] expectedDegrees = new Integer[] {
                11, 3, 6, 5, 2, 1, 2, 11, 1, 3, 9, 10, 10, 4, 2, 1, 1, 2, 1, 1, 5, 2, 4, 10, 3, 2, 1, 1, 3, 11, 2, 6, 2, 9, 9, 2, 6, 7, 2, 5, 2, 4, 3, 1, 6, 11, 2, 11, 3, 1, 6, 3, 1, 4, 5, 3, 6, 1, 1, 3, 1, 2, 2, 1, 4, 5, 1, 1, 9, 1, 1, 6, 4, 1, 5, 1, 2, 2, 3, 1, 1, 5, 2, 6, 1, 7, 11, 1, 8, 1, 5, 1, 1, 2, 2, 6, 4, 10, 1, 2, 5, 5, 5, 1, 1, 4, 1, 1, 1, 3, 5, 5, 5, 1, 4, 3, 3, 5, 1, 11, 3, 2, 8, 1, 2, 1, 1, 4, 5, 2, 1, 1, 1, 5, 6, 11, 10, 7, 4, 7, 1, 5, 3, 1, 1, 9, 1, 2, 5, 5, 2, 2, 3, 10, 1, 3, 2, 3, 3, 1, 1, 2, 1, 3, 2, 2, 1, 3, 8, 4, 1, 11, 6, 3, 1, 1, 1, 1, 1, 3, 1, 2, 1, 10, 1, 1, 8, 2, 7, 1, 2, 1, 9, 2, 10, 2, 1, 3, 4, 10
        };
        Assert.assertEquals(Arrays.asList(expectedDegrees), degrees);
    }

    @Test
    public void testChooseFragment() {
        byte[] message = URTest.makeMessage(1024, "Wolf");
        CRC32 crc32 = new CRC32();
        crc32.update(message);
        long checksum = crc32.getValue();
        int fragmentLen = FountainEncoder.findNominalFragmentLength(message.length, 10, 100);
        List<byte[]> fragments = FountainEncoder.partitionMessage(message, fragmentLen);
        List<List<Integer>> partIndexes = IntStream.rangeClosed(1, 30).mapToObj(nonce -> {
            return FountainUtils.chooseFragments(nonce, fragments.size(), checksum).stream().sorted().collect(Collectors.toList());
        }).collect(Collectors.toList());

        Integer[][] expectedFragmentIndexes = new Integer[][] {
                {0},
                {1},
                {2},
                {3},
                {4},
                {5},
                {6},
                {7},
                {8},
                {9},
                {10},
                {9},
                {2, 5, 6, 8, 9, 10},
                {8},
                {1, 5},
                {1},
                {0, 2, 4, 5, 8, 10},
                {5},
                {2},
                {2},
                {0, 1, 3, 4, 5, 7, 9, 10},
                {0, 1, 2, 3, 5, 6, 8, 9, 10},
                {0, 2, 4, 5, 7, 8, 9, 10},
                {3, 5},
                {4},
                {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10},
                {0, 1, 3, 4, 5, 6, 7, 9, 10},
                {6},
                {5, 6},
                {7}
        };

        List<List<Integer>> expectedPartIndexes = Arrays.stream(expectedFragmentIndexes).map(Arrays::asList).collect(Collectors.toList());
        Assert.assertEquals(expectedPartIndexes, partIndexes);
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
                "850a091901001a0167aa07581d330f0f33a05eead4f331df229871bee733b50de71afd2e5a79f196de09",
                "850b091901001a0167aa07581d3b205ce5e52d8c24a52cffa34c564fa1af3fdffcd349dc4258ee4ee828",
                "850c091901001a0167aa07581ddd7bf725ea6c16d531b5f03254783803048ca08b87148daacd1cd7a006",
                "850d091901001a0167aa07581d760be7ad1c6187902bbc04f539b9ee5eb8ea6833222edea36031306c01",
                "850e091901001a0167aa07581d5bf4031217d2c3254b088fa7553778b5003632f46e21db129416f65b55",
                "850f091901001a0167aa07581d73f021c0e6f65b05c0a494e50791270a0050a73ae69b6725505a2ec8a5",
                "8510091901001a0167aa07581db8546ebfe2048541348910267331c643133f828afec9337c318f71b7df",
                "8511091901001a0167aa07581d23dedeea74e3a0fb052befabefa13e2f80e4315c9dceed4c8630612e64",
                "8512091901001a0167aa07581dd01a8daee769ce34b6b35d3ca0005302724abddae405bdb419c0a6b208",
                "8513091901001a0167aa07581d3171c5dc365766eff25ae47c6f10e7de48cfb8474e050e5fe997a6dc24",
                "8514091901001a0167aa07581de055c2433562184fa71b4be94f262e200f01c6f74c284b0dc6fae6673f"
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
