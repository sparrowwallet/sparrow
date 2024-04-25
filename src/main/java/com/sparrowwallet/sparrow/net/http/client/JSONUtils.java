package com.sparrowwallet.sparrow.net.http.client;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

public class JSONUtils {
    private static JSONUtils instance;

    private ObjectMapper objectMapper;

    public JSONUtils() {
        objectMapper = new ObjectMapper();
        objectMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        objectMapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
    }

    public static final JSONUtils getInstance() {
        if(instance == null) {
            instance = new JSONUtils();
        }
        return instance;
    }

    public ObjectMapper getObjectMapper() {
        return objectMapper;
    }
}
