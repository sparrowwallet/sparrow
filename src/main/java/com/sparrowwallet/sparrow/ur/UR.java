package com.sparrowwallet.sparrow.ur;

import co.nstant.in.cbor.CborBuilder;
import co.nstant.in.cbor.CborDecoder;
import co.nstant.in.cbor.CborEncoder;
import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.model.ByteString;
import co.nstant.in.cbor.model.DataItem;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Ported from https://github.com/BlockchainCommons/URKit
 */
public class UR {
    public static final String UR_PREFIX = "ur";
    public static final String BYTES_TYPE = "bytes";

    private final String type;
    private final byte[] data;

    public UR(String type, byte[] data) throws InvalidTypeException {
        if(!isURType(type)) {
            throw new InvalidTypeException("Invalid UR type: " + type);
        }

        this.type = type;
        this.data = data;
    }

    public String getType() {
        return type;
    }

    public byte[] getCbor() {
        return data;
    }

    public byte[] toBytes() throws InvalidTypeException, CborException {
        if(!BYTES_TYPE.equals(getType())) {
            throw new InvalidTypeException("Not a " + BYTES_TYPE + " type");
        }

        ByteArrayInputStream bais = new ByteArrayInputStream(getCbor());
        List<DataItem> dataItems = new CborDecoder(bais).decode();
        return ((ByteString)dataItems.get(0)).getBytes();
    }

    public static boolean isURType(String type) {
        for(char c : type.toCharArray()) {
            if('a' <= c && c <= 'z') {
                return true;
            }
            if('0' <= c && c <= '9') {
                return true;
            }
            if(c == '-') {
                return true;
            }
        }

        return false;
    }

    public static UR fromBytes(byte[] data) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            new CborEncoder(baos).encode(new CborBuilder()
                    .add(data)
                    .build());
            byte[] cbor = baos.toByteArray();

            return new UR("bytes", cbor);
        } catch(InvalidTypeException | CborException e) {
            return null;
        }
    }

    @Override
    public boolean equals(Object o) {
        if(this == o) {
            return true;
        }
        if(o == null || getClass() != o.getClass()) {
            return false;
        }
        UR ur = (UR) o;
        return type.equals(ur.type) &&
                Arrays.equals(data, ur.data);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(type);
        result = 31 * result + Arrays.hashCode(data);
        return result;
    }

    public static class URException extends Exception {
        public URException(String message) {
            super(message);
        }
    }

    public static class InvalidTypeException extends URException {
        public InvalidTypeException(String message) {
            super(message);
        }
    }

    public static class InvalidSchemeException extends URException {
        public InvalidSchemeException(String message) {
            super(message);
        }
    }

    public static class InvalidPathLengthException extends URException {
        public InvalidPathLengthException(String message) {
            super(message);
        }
    }

    public static class InvalidSequenceComponentException extends URException {
        public InvalidSequenceComponentException(String message) {
            super(message);
        }
    }
}
