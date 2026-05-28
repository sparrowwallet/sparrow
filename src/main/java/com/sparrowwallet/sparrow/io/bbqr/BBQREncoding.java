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
            byte[] decoded = Utils.hexToBytes(part);
            checkDecodedLength(decoded.length);
            return decoded;
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
            byte[] decoded = BaseEncoding.base32().decode(part);
            checkDecodedLength(decoded.length);
            return decoded;
        }

        @Override
        public int getPartModulo() {
            return 8;
        }
    }, ZLIB("Z") {
        @Override
        public String encode(byte[] data) throws BBQREncodingException {
            return BASE32.encode(data);
        }

        @Override
        public byte[] deflate(byte[] data) throws BBQREncodingException {
            try {
                Deflater deflater = new Deflater(JZlib.Z_BEST_COMPRESSION, 10, true);
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                DeflaterOutputStream zOut = new DeflaterOutputStream(out, deflater);
                zOut.write(data);
                zOut.close();

                return out.toByteArray();
            } catch(Exception e) {
                throw new BBQREncodingException("Error deflating with zlib", e);
            }
        }

        @Override
        public byte[] decode(String part) throws BBQREncodingException {
            return BASE32.decode(part);
        }

        @Override
        public byte[] inflate(byte[] data) throws BBQREncodingException {
            try(ByteArrayInputStream in = new ByteArrayInputStream(data);
                InflaterInputStream zIn = new InflaterInputStream(in, new Inflater(10, true));
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while((bytesRead = zIn.read(buffer)) != -1) {
                    if(out.size() > MAX_DECODED_DATA_LENGTH - bytesRead) {
                        throw new BBQREncodingException("Decoded BBQr data exceeds maximum size of " + MAX_DECODED_DATA_LENGTH + " bytes");
                    }
                    out.write(buffer, 0, bytesRead);
                }

                return out.toByteArray();
            } catch(BBQREncodingException e) {
                throw e;
            } catch(Exception e) {
                throw new BBQREncodingException("Error inflating with zlib", e);
            }
        }

        @Override
        public int getPartModulo() {
            return 8;
        }
    };

    // BBQr targets PSBT and transaction payloads up to 500k; enforce the cap before parsing decoded data.
    public static final int MAX_DECODED_DATA_LENGTH = 500_000;

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

    public byte[] deflate(byte[] data) throws BBQREncodingException {
        return data;
    }

    public byte[] inflate(byte[] data) throws BBQREncodingException {
        checkDecodedLength(data.length);
        return data;
    }

    static void checkDecodedLength(int length) throws BBQREncodingException {
        if(length > MAX_DECODED_DATA_LENGTH) {
            throw new BBQREncodingException("Decoded BBQr data exceeds maximum size of " + MAX_DECODED_DATA_LENGTH + " bytes");
        }
    }

    public abstract String encode(byte[] data) throws BBQREncodingException;

    public abstract byte[] decode(String part) throws BBQREncodingException;

    public abstract int getPartModulo();
}
