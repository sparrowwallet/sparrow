package com.sparrowwallet.sparrow.ur.fountain;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Ported from https://github.com/BlockchainCommons/URKit
 */
public class FountainUtils {
    static List<Integer> chooseFragments(long seqNum, int seqLen, long checkSum) {
        // The first `seqLen` parts are the "pure" fragments, not mixed with any
        // others. This means that if you only generate the first `seqLen` parts,
        // then you have all the parts you need to decode the message.
        if(seqNum <= seqLen) {
            return List.of((int)seqNum - 1);
        } else {
            ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES * 2);
            buffer.putInt((int)(seqNum));
            buffer.putInt((int)(checkSum));

            RandomXoshiro256StarStar rng = new RandomXoshiro256StarStar(buffer.array());
            int degree = chooseDegree(seqLen, rng);
            List<Integer> indexes = IntStream.range(0, seqLen).boxed().collect(Collectors.toList());
            List<Integer> shuffledIndexes = shuffled(indexes, rng);
            return new ArrayList<>(shuffledIndexes.subList(0, degree));
        }
    }

    static int chooseDegree(int seqLen, RandomXoshiro256StarStar rng) {
        List<Double> degreeProbabilties = IntStream.range(1, seqLen + 1).mapToObj(i -> 1 / (double)i).collect(Collectors.toList());
        AliasMethod degreeChooser = new AliasMethod(degreeProbabilties, rng);
        return degreeChooser.next() + 1;
    }

    static List<Integer> shuffled(List<Integer> indexes, RandomXoshiro256StarStar rng) {
        List<Integer> remaining = new ArrayList<>(indexes);
        List<Integer> shuffled = new ArrayList<>(indexes.size());

        while(!remaining.isEmpty()) {
            int index = rng.nextInt(0, remaining.size());
            Integer item = remaining.remove(index);
            shuffled.add(item);
        }

        return shuffled;
    }
}
