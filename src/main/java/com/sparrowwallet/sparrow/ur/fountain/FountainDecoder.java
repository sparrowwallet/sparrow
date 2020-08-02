package com.sparrowwallet.sparrow.ur.fountain;

import com.sparrowwallet.sparrow.ur.ResultType;

import java.io.ByteArrayOutputStream;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.zip.CRC32;

import static com.sparrowwallet.sparrow.ur.fountain.FountainUtils.chooseFragments;

/**
 * Ported from https://github.com/BlockchainCommons/URKit
 */
public class FountainDecoder {
    private final Set<Integer> recievedPartIndexes = new TreeSet<>();
    private Set<Integer> lastPartIndexes;
    private int processedPartsCount = 0;
    private Result result;
    private long checksum;

    private Set<Integer> expectedPartIndexes;
    private int expectedFragmentLen;
    private int expectedMessageLen;
    private long expectedChecksum;

    private final Map<List<Integer>, Part> simpleParts = new HashMap<>();
    private Map<List<Integer>, Part> mixedParts = new HashMap<>();
    private final List<Part> queuedParts = new ArrayList<>();

    public int getExpectedPartCount() {
        return expectedPartIndexes.size();
    }

    public Set<Integer> getRecievedPartIndexes() {
        return recievedPartIndexes;
    }

    public Set<Integer> getLastPartIndexes() {
        return lastPartIndexes;
    }

    public int getProcessedPartsCount() {
        return processedPartsCount;
    }

    public double getEstimatedPercentComplete() {
        double estimatedInputParts = (double)getExpectedPartCount() * 1.75;
        return Math.min(0.99, (double)processedPartsCount / estimatedInputParts);
    }

    public Result getResult() {
        return result;
    }

    private static class Part {
        private final List<Integer> partIndexes;
        private final byte[] data;

        private int getIndex() {
            return partIndexes.get(0);
        }

        Part(FountainEncoder.Part part) {
            this.partIndexes = chooseFragments(part.getSeqNum(), part.getSeqLen(), part.getChecksum());
            this.data = part.getData();
        }

        Part(List<Integer> indexes, byte[] data) {
            this.partIndexes = indexes;
            this.data = data;
        }

        public boolean isSimple() {
            return partIndexes.size() == 1;
        }
    }

    public static class Result {
        public final ResultType type;
        public final byte[] data;
        public final String error;

        public Result(ResultType type, byte[] data, String error) {
            this.type = type;
            this.data = data;
            this.error = error;
        }
    }

    public boolean receivePart(FountainEncoder.Part encoderPart) {
        // Don't process the part if we're already done
        if(result != null) {
            return false;
        }

        // Don't continue if this part doesn't validate
        if(!validatePart(encoderPart)) {
             return false;
        }

        // Add this part to the queue
        Part part = new Part(encoderPart);
        lastPartIndexes = new HashSet<>(part.partIndexes);
        enqueue(part);

        // Process the queue until we're done or the queue is empty
        while(result == null && !queuedParts.isEmpty()) {
            processQueueItem();
        }

        // Keep track of how many parts we've processed
        processedPartsCount += 1;
        //printPartEnd();

        return true;
    }

    private void enqueue(Part part) {
        queuedParts.add(part);
    }

    private void printPartEnd() {
        int percent = (int)Math.round(getEstimatedPercentComplete() * 100);
        System.out.println("processed: " + processedPartsCount + " expected: " + getExpectedPartCount() + " received: " + recievedPartIndexes.size() + " percent: " + percent + "%");
    }

    private void printPart(Part part) {
        List<Integer> sorted = part.partIndexes.stream().sorted().collect(Collectors.toList());
        System.out.println("part indexes: " + sorted);
    }

    private void printState() {
        List<Integer> sortedReceived = recievedPartIndexes.stream().sorted().collect(Collectors.toList());
        List<List<Integer>> mixed = mixedParts.keySet().stream().map(list -> {
            list.sort(Comparator.naturalOrder());
            return list;
        }).collect(Collectors.toList());

        System.out.println("parts: " + getExpectedPartCount() + ", received: " + sortedReceived + ", mixed: " + mixed + ", queued: " + queuedParts.size() + ", result: " + result);
    }

    private void processQueueItem() {
        Part part = queuedParts.remove(0);

        //printPart(part);

        if(part.isSimple()) {
            processSimplePart(part);
        } else {
            processMixedPart(part);
        }

        //printState();
    }

    private void reduceMixed(Part by) {
        // Reduce all the current mixed parts by the given part
        List<Part> reducedParts = mixedParts.values().stream().map(part -> reducePart(part, by)).collect(Collectors.toList());

        // Collect all the remaining mixed parts
        Map<List<Integer>, Part> newMixed = new HashMap<>();
        reducedParts.forEach(reducedPart -> {
            // If this reduced part is now simple
            if(reducedPart.isSimple()) {
                // Add it to the queue
                enqueue(reducedPart);
            } else {
                // Otherwise, add it to the list of current mixed parts
                newMixed.put(reducedPart.partIndexes, reducedPart);
            }
        });

        mixedParts = newMixed;
    }

    // Reduce part `a` by part `b`
    private Part reducePart(Part a, Part b) {
        // If the fragments mixed into `b` are a strict (proper) subset of those in `a`...
        if(a.partIndexes.containsAll(b.partIndexes)) {
            // The new fragments in the revised part are `a` - `b`.
            List<Integer> newIndexes = new ArrayList<>(a.partIndexes);
            newIndexes.removeAll(b.partIndexes);

            // The new data in the revised part are `a` XOR `b`
            byte[] newdata = FountainEncoder.xor(a.data, b.data);
            return new Part(newIndexes, newdata);
        } else {
            // `a` is not reducable by `b`, so return a
            return a;
        }
    }

    private void processSimplePart(Part part) {
        // Don't process duplicate parts
        Integer fragmentIndex = part.partIndexes.get(0);
        if(recievedPartIndexes.contains(fragmentIndex)) {
            return;
        }

        // Record this part
        simpleParts.put(part.partIndexes, part);
        recievedPartIndexes.add(fragmentIndex);

        // If we've received all the parts
        if(recievedPartIndexes.equals(expectedPartIndexes)) {
            // Reassemble the message from its fragments
            List<Part> sortedParts = simpleParts.values().stream().sorted(Comparator.comparingInt(Part::getIndex)).collect(Collectors.toList());
            List<byte[]> fragments = sortedParts.stream().map(part1 -> part1.data).collect(Collectors.toList());
            byte[] message = joinFragments(fragments, expectedMessageLen);

            // Verify the message checksum and note success or failure
            CRC32 crc32 = new CRC32();
            crc32.update(message);
            checksum = crc32.getValue();

            if(checksum == expectedChecksum) {
                result = new Result(ResultType.SUCCESS, message, null);
            } else {
                result = new Result(ResultType.FAILURE, null, "Invalid checksum");
            }
        } else {
            // Reduce all the mixed parts by this part
            reduceMixed(part);
        }
    }

    private void processMixedPart(Part part) {
        // Don't process duplicate parts
        if(mixedParts.containsKey(part.partIndexes)) {
            return;
        }

        // Reduce this part by all the others
        List<Part> allParts = new ArrayList<>(simpleParts.values());
        allParts.addAll(mixedParts.values());
        Part p = allParts.stream().reduce(part, this::reducePart);

        // If the part is now simple
        if(p.isSimple()) {
            // Add it to the queue
            enqueue(p);
        } else {
            // Reduce all the mixed parts by this one
            reduceMixed(p);
            // Record this new mixed part
            mixedParts.put(p.partIndexes, p);
        }
    }

    private boolean validatePart(FountainEncoder.Part part) {
        // If this is the first part we've seen
        if(expectedPartIndexes == null) {
            // Record the things that all the other parts we see will have to match to be valid.
            expectedPartIndexes = IntStream.range(0, part.getSeqLen()).boxed().collect(Collectors.toSet());
            expectedMessageLen = part.getMessageLen();
            expectedChecksum = part.getChecksum();
            expectedFragmentLen = part.getData().length;

            return true;
        } else {
            return getExpectedPartCount() == part.getSeqLen() && expectedMessageLen == part.getMessageLen() && expectedChecksum == part.getChecksum() && expectedFragmentLen == part.getData().length;
        }
    }

    static byte[] joinFragments(List<byte[]> fragments, int messageLen) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        fragments.forEach(baos::writeBytes);
        byte[] message = baos.toByteArray();

        byte[] unpaddedMessage = new byte[messageLen];
        System.arraycopy(message, 0, unpaddedMessage, 0, messageLen);

        return unpaddedMessage;
    }
}
