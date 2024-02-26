package com.sparrowwallet.sparrow.io.bbqr;

import java.util.ArrayList;
import java.util.List;

public class BBQREncoder {
    private final String[] parts;
    private int partIndex;

    public BBQREncoder(BBQRType bbqrType, BBQREncoding bbqrEncoding, byte[] data, int maxFragmentLength, int firstSeqNum) {
        this.parts = encode(bbqrType, bbqrEncoding, data, maxFragmentLength).toArray(new String[0]);
        this.partIndex = firstSeqNum;
    }

    public boolean isSinglePart() {
        return parts.length == 1;
    }

    public String nextPart() {
        String currentPart = parts[partIndex];
        partIndex++;
        if(partIndex > parts.length - 1) {
            partIndex = 0;
        }

        return currentPart;
    }

    public int getNumParts() {
        return parts.length;
    }

    private List<String> encode(BBQRType type, BBQREncoding desiredEncoding, byte[] data, int desiredChunkSize) {
        String encoded;
        BBQREncoding encoding = desiredEncoding;

        try {
            encoded = encoding.encode(data);
            if(encoding == BBQREncoding.ZLIB) {
                String uncompressed = BBQREncoding.BASE32.encode(data);
                if(encoded.length() > uncompressed.length()) {
                    throw new BBQREncodingException("Compressed data was larger than uncompressed data");
                }
            }
        } catch(BBQREncodingException e) {
            encoding = BBQREncoding.BASE32;
            encoded = BBQREncoding.BASE32.encode(data);
        }

        int inputLength = encoded.length();
        int numChunks = (inputLength + desiredChunkSize - 1) / desiredChunkSize;
        int chunkSize = numChunks == 1 ? desiredChunkSize : (int)Math.ceil((double)inputLength / numChunks);

        int modulo = chunkSize % encoding.getPartModulo();
        if(modulo > 0) {
            chunkSize += (encoding.getPartModulo() - modulo);
        }

        List<String> chunks = new ArrayList<>();
        int startIndex = 0;
        for(int i = 0; i < numChunks; i ++) {
            int endIndex = Math.min(startIndex + chunkSize, encoded.length());
            BBQRHeader bbqrHeader = new BBQRHeader(encoding, type, numChunks, i);
            String chunk = bbqrHeader + encoded.substring(startIndex, endIndex);
            startIndex = endIndex;
            chunks.add(chunk);
        }

        return chunks;
    }
}
