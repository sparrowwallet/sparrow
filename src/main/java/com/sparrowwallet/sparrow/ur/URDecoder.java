package com.sparrowwallet.sparrow.ur;

import co.nstant.in.cbor.CborException;
import com.sparrowwallet.sparrow.ur.fountain.FountainDecoder;
import com.sparrowwallet.sparrow.ur.fountain.FountainEncoder;

import java.util.Arrays;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Ported from https://github.com/BlockchainCommons/URKit
 */
public class URDecoder {
    private static final Pattern SEQUENCE_COMPONENT_PATTERN = Pattern.compile("(\\d+)-(\\d+)");

    private final FountainDecoder fountainDecoder;

    private String expectedType;
    private Result result;

    public URDecoder() {
        this.fountainDecoder = new FountainDecoder();
    }

    public int getExpectedPartCount() {
        return fountainDecoder.getExpectedPartCount();
    }

    public Set<Integer> getReceivedPartIndexes() {
        return fountainDecoder.getRecievedPartIndexes();
    }

    public Set<Integer> getLastPartIndexes() {
        return fountainDecoder.getLastPartIndexes();
    }

    public int getProcessedPartsCount() {
        return fountainDecoder.getProcessedPartsCount();
    }

    public double getEstimatedPercentComplete() {
        return fountainDecoder.getEstimatedPercentComplete();
    }

    public Result getResult() {
        return result;
    }

    public static UR decode(String string) throws UR.URException {
        ParsedURString parsedURString = parse(string);

        if(parsedURString.components.length < 1) {
            throw new UR.InvalidPathLengthException("Invalid path length");
        }

        String body = parsedURString.components[0];
        return decode(parsedURString.type, body);
    }

    public static UR decode(String type, String body) throws UR.InvalidTypeException {
        byte[] cbor = Bytewords.decode(body, Bytewords.Style.MINIMAL);
        return new UR(type, cbor);
    }

    public boolean receivePart(String string) {
        try {
            // Don't process the part if we're already done
            if(getResult() != null) {
                return false;
            }

            // Don't continue if this part doesn't validate
            ParsedURString parsedURString = parse(string);
            if(!validatePart(parsedURString.type)) {
                return false;
            }

            // If this is a single-part UR then we're done
            if(parsedURString.components.length == 1) {
                String body = parsedURString.components[0];
                result = new Result(ResultType.SUCCESS, decode(parsedURString.type, body), null);
                return true;
            }

            // Multi-part URs must have two path components: seq/fragment
            if(parsedURString.components.length != 2) {
                throw new UR.InvalidPathLengthException("Invalid path length");
            }

            String seq = parsedURString.components[0];
            String fragment = parsedURString.components[1];

            // Parse the sequence component and the fragment, and
            // make sure they agree.
            Matcher matcher = SEQUENCE_COMPONENT_PATTERN.matcher(seq);
            if(matcher.matches()) {
                int seqNum = Integer.parseInt(matcher.group(1));
                int seqLen = Integer.parseInt(matcher.group(2));

                byte[] cbor = Bytewords.decode(fragment, Bytewords.Style.MINIMAL);
                FountainEncoder.Part part = FountainEncoder.Part.fromCborBytes(cbor);

                if(seqNum != part.getSeqNum() || seqLen != part.getSeqLen()) {
                    return false;
                }

                if(!fountainDecoder.receivePart(part)) {
                    return false;
                }

                if(fountainDecoder.getResult() == null) {
                    //Not done yet
                } else if(fountainDecoder.getResult().type == ResultType.SUCCESS) {
                    result = new Result(ResultType.SUCCESS, new UR(parsedURString.type, fountainDecoder.getResult().data), null);
                } else if(fountainDecoder.getResult().type == ResultType.FAILURE) {
                    result = new Result(ResultType.FAILURE, null, fountainDecoder.getResult().error);
                }

                return true;
            } else {
                throw new UR.InvalidSequenceComponentException("Invalid sequence " + seq);
            }
        } catch(UR.URException | CborException e) {
            return false;
        }
    }

    private boolean validatePart(String type) {
        if(expectedType == null) {
            if(!UR.isURType(type)) {
                return false;
            }
            expectedType = type;
        } else {
            return expectedType.equals(type);
        }

        return true;
    }

    static ParsedURString parse(String string) throws UR.URException {
        // Don't consider case
        String lowercased = string.toLowerCase();

        // Validate URI scheme
        if(!lowercased.startsWith("ur:")) {
            throw new UR.InvalidSchemeException("Invalid scheme");
        }
        String path = lowercased.substring(3);

        // Split the remainder into path components
        String[] components = path.split("/");

        // Make sure there are at least two path components
        if(components.length <= 1) {
            throw new UR.InvalidPathLengthException("Invalid path length");
        }

        // Validate the type
        String type = components[0];
        if(!UR.isURType(type)) {
            throw new UR.InvalidTypeException("Invalid type: " + type);
        }

        return new ParsedURString(type, Arrays.copyOfRange(components, 1, components.length));
    }

    private static class ParsedURString {
        public final String type;
        public final String[] components;

        public ParsedURString(String type, String[] components) {
            this.type = type;
            this.components = components;
        }
    }

    public static class Result {
        public final ResultType type;
        public final UR ur;
        public final String error;

        public Result(ResultType type, UR ur, String error) {
            this.type = type;
            this.ur = ur;
            this.error = error;
        }
    }
}
