package com.sparrowwallet.sparrow.timelockrecovery;

import com.sparrowwallet.drongo.Utils;
import com.sparrowwallet.drongo.protocol.Sha256Hash;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * BIP-128 checksum: SHA-256 of ECMAScript JSON.stringify(Object.entries(obj).sort()), first 8 hex chars.
 * Optional fields without a value and the checksum field itself are omitted.
 */
public final class Bip128Json {
    private Bip128Json() {
    }

    public static String checksum(Map<String, Object> fields) {
        List<Map.Entry<String, Object>> entries = new ArrayList<>();
        for(Map.Entry<String, Object> entry : fields.entrySet()) {
            if(entry.getValue() == null || "checksum".equals(entry.getKey())) {
                continue;
            }
            entries.add(entry);
        }
        entries.sort(Map.Entry.comparingByKey());

        byte[] hash = Sha256Hash.hash(stringifyEntries(entries).getBytes(StandardCharsets.UTF_8));
        return Utils.bytesToHex(hash).substring(0, 8);
    }

    public static Map<String, Object> withChecksum(Map<String, Object> fields) {
        Map<String, Object> copy = new LinkedHashMap<>(fields);
        copy.remove("checksum");
        copy.put("checksum", checksum(copy));
        return copy;
    }

    static String stringifyEntries(List<Map.Entry<String, Object>> entries) {
        StringBuilder builder = new StringBuilder();
        builder.append('[');
        for(int i = 0; i < entries.size(); i++) {
            if(i > 0) {
                builder.append(',');
            }
            Map.Entry<String, Object> entry = entries.get(i);
            builder.append('[');
            stringify(builder, entry.getKey());
            builder.append(',');
            stringify(builder, entry.getValue());
            builder.append(']');
        }
        builder.append(']');
        return builder.toString();
    }

    static void stringify(StringBuilder builder, Object value) {
        if(value == null) {
            builder.append("null");
        } else if(value instanceof String string) {
            stringifyString(builder, string);
        } else if(value instanceof Boolean bool) {
            builder.append(bool ? "true" : "false");
        } else if(value instanceof Number number) {
            double asDouble = number.doubleValue();
            if(asDouble == Math.rint(asDouble) && !Double.isInfinite(asDouble)) {
                builder.append(number.longValue());
            } else {
                builder.append(number);
            }
        } else if(value instanceof Collection<?> collection) {
            builder.append('[');
            int i = 0;
            for(Object element : collection) {
                if(i++ > 0) {
                    builder.append(',');
                }
                stringify(builder, element);
            }
            builder.append(']');
        } else if(value instanceof Object[] array) {
            stringify(builder, List.of(array));
        } else {
            throw new IllegalArgumentException("Unsupported JSON value type: " + value.getClass().getName());
        }
    }

    private static void stringifyString(StringBuilder builder, String string) {
        builder.append('"');
        for(int i = 0; i < string.length(); i++) {
            char ch = string.charAt(i);
            switch(ch) {
                case '"' -> builder.append("\\\"");
                case '\\' -> builder.append("\\\\");
                case '\b' -> builder.append("\\b");
                case '\f' -> builder.append("\\f");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                default -> {
                    if(ch < 0x20) {
                        builder.append(String.format("\\u%04x", (int)ch));
                    } else {
                        builder.append(ch);
                    }
                }
            }
        }
        builder.append('"');
    }
}
