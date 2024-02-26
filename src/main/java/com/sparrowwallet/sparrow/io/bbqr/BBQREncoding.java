package com.sparrowwallet.sparrow.io.bbqr;

import com.google.common.io.BaseEncoding;
import com.jcraft.jzlib.*;
import com.sparrowwallet.drongo.Utils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Locale;

public enum BBQREncoding {
    HEX("H") {
        @Override
        public String encode(byte[] data) throws BBQREncodingException {
            return Utils.bytesToHex(data).toUpperCase(Locale.ROOT);
        }

        @Override
        public byte[] decode(String part) throws BBQREncodingException {
            return Utils.hexToBytes(part);
        }

        @Override
        public int getPartModulo() {
            return 2;
        }
    }, BASE32("2") {
        @Override
        public String encode(byte[] data) throws BBQREncodingException {
            return BaseEncoding.base32().encode(data).replaceAll("=+$", "");
        }

        @Override
        public byte[] decode(String part) throws BBQREncodingException {
            return BaseEncoding.base32().decode(part);
        }

        @Override
        public int getPartModulo() {
            return 8;
        }
    }, ZLIB("Z") {
        @Override
        public String encode(byte[] data) throws BBQREncodingException {
            try {
                Deflater deflater = new Deflater(JZlib.Z_BEST_COMPRESSION, 10, true);
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                DeflaterOutputStream zOut = new DeflaterOutputStream(out, deflater);
                zOut.write(data);
                zOut.close();

                return BASE32.encode(out.toByteArray());
            } catch(Exception e) {
                throw new BBQREncodingException("Error deflating with zlib", e);
            }
        }

        @Override
        public byte[] decode(String part) throws BBQREncodingException {
            try {
                Inflater inflater = new Inflater(10, true);
                ByteArrayInputStream in = new ByteArrayInputStream(BASE32.decode(part));
                InflaterInputStream zIn = new InflaterInputStream(in, inflater);
                byte[] decoded = zIn.readAllBytes();
                zIn.close();

                return decoded;
            } catch(Exception e) {
                throw new BBQREncodingException("Error inflating with zlib", e);
            }
        }

        @Override
        public int getPartModulo() {
            return 8;
        }
    };

    public static BBQREncoding fromString(String code) {
        for(BBQREncoding encoding : values()) {
            if(encoding.getCode().equals(code)) {
                return encoding;
            }
        }

        throw new IllegalArgumentException("Could not find encoding for code " + code);
    }

    private final String code;

    BBQREncoding(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public abstract String encode(byte[] data) throws BBQREncodingException;

    public abstract byte[] decode(String part) throws BBQREncodingException;

    public abstract int getPartModulo();
}
