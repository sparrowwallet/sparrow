package com.sparrowwallet.sparrow.io.bbqr;

import com.sparrowwallet.drongo.protocol.Transaction;
import com.sparrowwallet.drongo.psbt.PSBT;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;

public class BBQRDecoder {
    private static final Logger log = LoggerFactory.getLogger(BBQRDecoder.class);

    private final Map<Integer, byte[]> receivedParts = new TreeMap<>();
    private int totalParts;

    private BBQRType type;
    private Result result;

    public static boolean isBBQRFragment(String part) {
        try {
            BBQRHeader.fromString(part);
            return true;
        } catch(Exception e) {
            return false;
        }
    }

    public void receivePart(String part) {
        try {
            BBQRHeader header = BBQRHeader.fromString(part);
            totalParts = header.seqTotal();
            type = header.type();
            byte[] partData = header.decode(part);
            receivedParts.put(header.seqNumber(), partData);

            if(receivedParts.size() == totalParts) {
                byte[] data = concatParts();

                if(type == BBQRType.PSBT) {
                    result = new Result(new PSBT(data));
                } else if(type == BBQRType.TXN) {
                    result = new Result(new Transaction(data));
                } else if(type == BBQRType.JSON || type == BBQRType.UNICODE) {
                    result = new Result(type, new String(data, StandardCharsets.UTF_8));
                } else {
                    result = new Result(type, data);
                }
            }
        } catch(Exception e) {
            log.error("Could not parse received QR of type " + type, e);
            result = new Result(ResultType.FAILURE, e.getMessage());
        }
    }

    private byte[] concatParts() {
        int totalLength = 0;
        for(byte[] part : receivedParts.values()) {
            totalLength += part.length;
        }

        byte[] data = new byte[totalLength];
        int index = 0;
        for(byte[] part : receivedParts.values()) {
            System.arraycopy(part, 0, data, index, part.length);
            index += part.length;
        }

        return data;
    }

    public int getProcessedPartsCount() {
        return receivedParts.size();
    }

    public double getPercentComplete() {
        if(totalParts == 0) {
            return 0d;
        }

        return (double)getProcessedPartsCount() / totalParts;
    }

    public Result getResult() {
        return result;
    }

    public static class Result {
        private final ResultType resultType;
        private final BBQRType bbqrType;
        private final PSBT psbt;
        private final Transaction transaction;
        private final String strData;
        private final byte[] data;
        private final String error;

        public Result(ResultType resultType, String error) {
            this.resultType = resultType;
            this.bbqrType = null;
            this.psbt = null;
            this.transaction = null;
            this.strData = null;
            this.data = null;
            this.error = error;
        }

        public Result(PSBT psbt) {
            this.resultType = ResultType.SUCCESS;
            this.bbqrType = BBQRType.PSBT;
            this.psbt = psbt;
            this.transaction = null;
            this.strData = null;
            this.data = null;
            this.error = null;
        }

        public Result(Transaction transaction) {
            this.resultType = ResultType.SUCCESS;
            this.bbqrType = BBQRType.TXN;
            this.psbt = null;
            this.transaction = transaction;
            this.strData = null;
            this.data = null;
            this.error = null;
        }

        public Result(BBQRType bbqrType, String strData) {
            this.resultType = ResultType.SUCCESS;
            this.bbqrType = bbqrType;
            this.psbt = null;
            this.transaction = null;
            this.strData = strData;
            this.data = null;
            this.error = null;
        }

        public Result(BBQRType bbqrType, byte[] data) {
            this.resultType = ResultType.SUCCESS;
            this.bbqrType = bbqrType;
            this.psbt = null;
            this.transaction = null;
            this.strData = null;
            this.data = data;
            this.error = null;
        }

        public ResultType getResultType() {
            return resultType;
        }

        public BBQRType getBbqrType() {
            return bbqrType;
        }

        public PSBT getPsbt() {
            return psbt;
        }

        public Transaction getTransaction() {
            return transaction;
        }

        public byte[] getData() {
            return data;
        }

        public String toString() {
            return strData;
        }

        public String getError() {
            return error;
        }
    }

    public enum ResultType {
        SUCCESS, FAILURE;
    }
}
