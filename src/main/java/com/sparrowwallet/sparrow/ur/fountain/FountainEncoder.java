package com.sparrowwallet.sparrow.ur.fountain;

import co.nstant.in.cbor.CborBuilder;
import co.nstant.in.cbor.CborDecoder;
import co.nstant.in.cbor.CborEncoder;
import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.ByteString;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.UnsignedInteger;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.CRC32;

import static com.sparrowwallet.sparrow.ur.fountain.FountainUtils.chooseFragments;

public class FountainEncoder {
    private final int messageLen;
    private final long checksum;
    private final int fragmentLen;
    private final List<byte[]> fragments;
    private final int seqLen;
    private List<Integer> partIndexes;
    private long seqNum;

    public FountainEncoder(byte[] message, int maxFragmentLen, int minFragmentLen, long firstSeqNum) {
        if(message.length >= Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Message too long");
        }

        this.messageLen = message.length;

        CRC32 crc32 = new CRC32();
        crc32.update(message);
        this.checksum = crc32.getValue();

        this.fragmentLen = findNominalFragmentLength(messageLen, minFragmentLen, maxFragmentLen);
        this.fragments = partitionMessage(message, fragmentLen);
        this.seqLen = fragments.size();
        this.seqNum = firstSeqNum;
    }

    public Part nextPart() {
        seqNum += 1;
        partIndexes = chooseFragments(seqNum, seqLen, checksum);
        byte[] mixed = mix(partIndexes);
        return new Part(seqNum, seqLen, messageLen, checksum, mixed);
    }

    private byte[] mix(List<Integer> partIndexes) {
        return partIndexes.stream().reduce(new byte[fragmentLen], (result, index) -> xor(fragments.get(index), result), FountainEncoder::xor);
    }

    public static byte[] xor(byte[] a, byte[] b) {
        byte[] result = new byte[a.length];
        for (int i = 0; i < result.length; i++) {
            result[i] = (byte) (((int) a[i]) ^ ((int) b[i]));
        }

        return result;
    }

    public boolean isComplete() {
        return seqNum >= seqLen;
    }

    public boolean isSinglePart() {
        return seqLen == 1;
    }

    public long getSeqNum() {
        return seqNum;
    }

    public int getSeqLen() {
        return seqLen;
    }

    public List<Integer> getPartIndexes() {
        return partIndexes;
    }

    static List<byte[]> partitionMessage(byte[] message, int fragmentLen) {
        int fragmentCount = (int)Math.ceil(message.length / (double)fragmentLen);
        List<byte[]> fragments = new ArrayList<>();

        int start = 0;
        for(int i = 0; i < fragmentCount; i++) {
            fragments.add(Arrays.copyOfRange(message, start, start + fragmentLen));
            start += fragmentLen;
        }

        return fragments;
    }

    static int findNominalFragmentLength(int messageLen, int minFragmentLen, int maxFragmentLen) {
        int maxFragmentCount = messageLen / minFragmentLen;
        int fragmentLen = 0;
        for(int fragmentCount = 1; fragmentCount <= maxFragmentCount; fragmentCount++) {
            fragmentLen = (int)Math.ceil((double)messageLen / (double)fragmentCount);
            if(fragmentLen <= maxFragmentLen) {
                break;
            }
        }

        return fragmentLen;
    }

    public static class Part {
        private final long seqNum;
        private final int seqLen;
        private final int messageLen;
        private final long checksum;
        private final byte[] data;

        public Part(long seqNum, int seqLen, int messageLen, long checksum, byte[] data) {
            this.seqNum = seqNum;
            this.seqLen = seqLen;
            this.messageLen = messageLen;
            this.checksum = checksum;
            this.data = data;
        }

        public long getSeqNum() {
            return seqNum;
        }

        public int getSeqLen() {
            return seqLen;
        }

        public int getMessageLen() {
            return messageLen;
        }

        public long getChecksum() {
            return checksum;
        }

        public byte[] getData() {
            return data;
        }

        public byte[] toCborBytes() {
            try {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                new CborEncoder(baos).encode(new CborBuilder()
                        .addArray()
                        .add(new UnsignedInteger(seqNum))
                        .add(new UnsignedInteger(seqLen))
                        .add(new UnsignedInteger(messageLen))
                        .add(new UnsignedInteger(checksum))
                        .add(data)
                        .end()
                        .build());

                return baos.toByteArray();
            } catch(Exception e) {
                throw new RuntimeException(e);
            }
        }

        public static Part fromCborBytes(byte[] cborData) throws CborException {
            ByteArrayInputStream bais = new ByteArrayInputStream(cborData);
            List<DataItem> arrayDataItems = new CborDecoder(bais).decode();
            Array array = (Array)arrayDataItems.get(0);
            List<DataItem> dataItems = array.getDataItems();

            UnsignedInteger seqNum = (UnsignedInteger)dataItems.get(0);
            UnsignedInteger seqLen = (UnsignedInteger)dataItems.get(1);
            UnsignedInteger messageLen = (UnsignedInteger)dataItems.get(2);
            UnsignedInteger checksum = (UnsignedInteger)dataItems.get(3);
            ByteString data = (ByteString)dataItems.get(4);

            return new Part(seqNum.getValue().longValue(), seqLen.getValue().intValue(), messageLen.getValue().intValue(), checksum.getValue().longValue(), data.getBytes());
        }
    }
}
