package com.sparrowwallet.sparrow.ur;

import com.sparrowwallet.sparrow.ur.fountain.FountainEncoder;

import java.util.Arrays;
import java.util.List;
import java.util.StringJoiner;

/**
 * Ported from https://github.com/BlockchainCommons/URKit
 */
public class UREncoder {
    private final UR ur;
    private final FountainEncoder fountainEncoder;

    public UREncoder(UR ur, int maxFragmentLen, int minFragmentLen, long firstSeqNum) {
        this.ur = ur;
        this.fountainEncoder = new FountainEncoder(ur.getCbor(), maxFragmentLen, minFragmentLen, firstSeqNum);
    }

    public boolean isComplete() {
        return fountainEncoder.isComplete();
    }

    public boolean isSinglePart() {
        return fountainEncoder.isSinglePart();
    }

    public String nextPart() {
        FountainEncoder.Part part = fountainEncoder.nextPart();
        if(isSinglePart()) {
            return encode(ur);
        } else {
            return encodePart(ur.getType(), part);
        }
    }

    public long getSeqNum() {
        return fountainEncoder.getSeqNum();
    }

    public int getSeqLen() {
        return fountainEncoder.getSeqLen();
    }

    public List<Integer> getPartIndexes() {
        return fountainEncoder.getPartIndexes();
    }

    public static String encode(UR ur) {
        String encoded = Bytewords.encode(ur.getCbor(), Bytewords.Style.MINIMAL);
        return encodeUR(ur.getType(), encoded);
    }

    private static String encodeUR(String... pathComponents) {
        return encodeURI(UR.UR_PREFIX, pathComponents);
    }

    private static String encodeURI(String scheme, String... pathComponents) {
        StringJoiner joiner = new StringJoiner("/");
        Arrays.stream(pathComponents).forEach(joiner::add);
        String path = joiner.toString();

        return scheme + ":" + path;
    }

    private static String encodePart(String type, FountainEncoder.Part part) {
        String seq = part.getSeqNum() + "-" + part.getSeqLen();
        String body = Bytewords.encode(part.toCborBytes(), Bytewords.Style.MINIMAL);
        return encodeUR(type, seq, body);
    }
}
