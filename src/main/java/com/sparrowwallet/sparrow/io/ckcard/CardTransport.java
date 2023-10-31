package com.sparrowwallet.sparrow.io.ckcard;

import co.nstant.in.cbor.CborBuilder;
import co.nstant.in.cbor.CborDecoder;
import co.nstant.in.cbor.CborEncoder;
import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.builder.ArrayBuilder;
import co.nstant.in.cbor.builder.MapBuilder;
import co.nstant.in.cbor.model.*;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.sparrowwallet.drongo.Utils;
import com.sparrowwallet.sparrow.io.CardAuthorizationException;
import com.sparrowwallet.sparrow.io.CardUnluckyNumberException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.smartcardio.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.*;
import java.util.Map;

public class CardTransport {
    private static final Logger log = LoggerFactory.getLogger(CardTransport.class);

    public static final String APPID = "f0436f696e6b697465434152447631";
    private static final int CBOR_CLA = 0x00;
    private static final int CBOR_INS = 0xCB;
    private static final int SW_OKAY = 0x9000;

    private final Card connection;

    CardTransport() throws CardException {
        TerminalFactory tf = TerminalFactory.getDefault();
        List<CardTerminal> terminals = tf.terminals().list();
        if(terminals.isEmpty()) {
            throw new IllegalStateException("No reader connected");
        }

        Card connection = null;
        for(Iterator<CardTerminal> iter = terminals.iterator(); iter.hasNext(); ) {
            try {
                connection = getConnection(iter.next());
                break;
            } catch(CardException e) {
                if(!iter.hasNext()) {
                    log.debug(e.getMessage());
                    throw e;
                }
            }
        }

        this.connection = connection;
    }

    private Card getConnection(CardTerminal cardTerminal) throws CardException {
        Card connection = cardTerminal.connect("*");

        CardChannel cardChannel = connection.getBasicChannel();
        ResponseAPDU resp = cardChannel.transmit(new CommandAPDU(0, 0xA4, 4, 0, Utils.hexToBytes(APPID.toUpperCase())));
        if(resp.getSW() != SW_OKAY) {
            throw new CardException("Card initialization error, response was 0x" + Integer.toHexString(resp.getSW()));
        }

        return connection;
    }

    JsonObject send(String cmd, Map<String, Object> args) throws CardException {
        Map<String, Object> sendMap = new LinkedHashMap<>();
        sendMap.put("cmd", cmd);
        sendMap.putAll(args);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        MapBuilder<CborBuilder> cborBuilder = new CborBuilder().addMap();
        for(Map.Entry<String, Object> entry : sendMap.entrySet()) {
            if(entry.getValue() instanceof String strValue) {
                cborBuilder.put(entry.getKey(), strValue);
            } else if(entry.getValue() instanceof byte[] byteValue) {
                cborBuilder.put(entry.getKey(), byteValue);
            } else if(entry.getValue() instanceof Long longValue) {
                cborBuilder.put(entry.getKey(), longValue);
            } else if(entry.getValue() instanceof Integer integerValue) {
                cborBuilder.put(entry.getKey(), integerValue);
            } else if(entry.getValue() instanceof Boolean booleanValue) {
                cborBuilder.put(entry.getKey(), booleanValue);
            } else if(entry.getValue() instanceof List<?> listValue) {
                ArrayBuilder<MapBuilder<CborBuilder>> arrayBuilder = cborBuilder.putArray(entry.getKey());
                for(Object value : listValue) {
                    if(value instanceof String strValue) {
                        arrayBuilder.add(strValue);
                    } else if(value instanceof byte[] byteValue) {
                        arrayBuilder.add(byteValue);
                    } else if(value instanceof Long longValue) {
                        arrayBuilder.add(longValue);
                    } else if(value instanceof Boolean booleanValue) {
                        arrayBuilder.add(booleanValue);
                    }
                }
                arrayBuilder.end();
            }
        }

        try {
            new CborEncoder(baos).encode(cborBuilder.end().build());
            byte[] sendBytes = baos.toByteArray();

            CardChannel cardChannel = connection.getBasicChannel();
            ResponseAPDU resp = cardChannel.transmit(new CommandAPDU(CBOR_CLA, CBOR_INS, 0, 0, sendBytes));

            if(resp.getSW() != SW_OKAY) {
                throw new CardException("Received error SW value " + resp.getSW());
            }

            ByteArrayInputStream bais = new ByteArrayInputStream(resp.getData());
            List<DataItem> dataItems = new CborDecoder(bais).decode();
            for(DataItem dataItem : dataItems) {
                if(dataItem instanceof co.nstant.in.cbor.model.Map map) {
                    JsonObject result = new JsonObject();
                    for(DataItem key : map.getKeys()) {
                        String strKey = key.toString();
                        result.add(strKey, getJsonElement(map.get(key)));
                    }

                    if(result.get("error") != null) {
                        String msg = result.get("error").getAsString();
                        int code = result.get("code") == null ? 500 : result.get("code").getAsInt();
                        if(code == 205) {
                            throw new CardUnluckyNumberException("Card chose unlucky number, please retry");
                        } else if(code == 401) {
                            throw new CardAuthorizationException("Incorrect PIN provided");
                        }

                        throw new CardException(code + " on " + cmd + ": " + msg);
                    }

                    return result;
                }
            }
        } catch(CborException e) {
            log.error("CBOR encoding error", e);
        }

        return new JsonObject();
    }

    private JsonElement getJsonElement(DataItem dataItem) {
        if(dataItem instanceof UnicodeString strValue) {
            return new JsonPrimitive(strValue.toString());
        } else if(dataItem instanceof ByteString byteString) {
            return new JsonPrimitive(Utils.bytesToHex(byteString.getBytes()));
        } else if(dataItem instanceof UnsignedInteger unsignedInteger) {
            return new JsonPrimitive(unsignedInteger.getValue());
        } else if(dataItem instanceof SimpleValue simpleValue) {
            return new JsonPrimitive(simpleValue.getValue() == SimpleValueType.TRUE.getValue());
        } else if(dataItem instanceof Array array) {
            JsonArray jsonArray = new JsonArray();
            for(DataItem item : array.getDataItems()) {
                jsonArray.add(getJsonElement(item));
            }
            return jsonArray;
        }

        throw new IllegalArgumentException("Cannot convert dataItem of type " + dataItem.getClass() + "to JsonElement");
    }

    void disconnect() throws CardException {
        connection.disconnect(true);
    }

    static boolean isReaderAvailable() {
        try {
            TerminalFactory tf = TerminalFactory.getDefault();
            return !tf.terminals().list().isEmpty();
        } catch(Exception e) {
            log.error("Error detecting card terminals", e);
        }

        return false;
    }
}
