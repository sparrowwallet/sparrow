package com.sparrowwallet.sparrow.net;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.arteam.simplejsonrpc.client.JsonRpcClient;
import com.github.arteam.simplejsonrpc.client.Transport;
import com.github.arteam.simplejsonrpc.client.builder.AbstractBuilder;
import com.github.arteam.simplejsonrpc.client.builder.BatchRequestBuilder;
import com.google.common.collect.Lists;
import com.sparrowwallet.sparrow.io.Config;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.sparrowwallet.sparrow.net.BatchedElectrumServerRpc.MAX_RETRIES;
import static com.sparrowwallet.sparrow.net.BatchedElectrumServerRpc.RETRY_DELAY;

public class PagedBatchRequestBuilder<K, V> extends AbstractBuilder {
    public static final int DEFAULT_PAGE_SIZE = 500;

    @NotNull
    private final List<Request> requests;

    /**
     * Type of request ids
     */
    @Nullable
    private final Class<K> keysType;

    /**
     * Expected return type for all requests
     * <p/>
     * This property works exclusively with {@code returnTypes}. Only one of them should be set.
     */
    @Nullable
    private final Class<V> returnType;

    /**
     * Creates a new batch request builder in an initial state
     *
     * @param transport transport for request performing
     * @param mapper    mapper for JSON processing
     */
    public PagedBatchRequestBuilder(@NotNull Transport transport, @NotNull ObjectMapper mapper) {
        this(transport, mapper, new ArrayList<Request>(), null, null);
    }

    public PagedBatchRequestBuilder(@NotNull Transport transport, @NotNull ObjectMapper mapper,
                                    @NotNull List<Request> requests,
                                    @Nullable Class<K> keysType, @Nullable Class<V> returnType) {
        super(transport, mapper);
        this.requests = requests;
        this.keysType = keysType;
        this.returnType = returnType;
    }

    /**
     * Adds a new request without specifying a return type
     *
     * @param id     request id as a text value
     * @param method request method
     * @param param request param
     * @return the current builder
     */
    @NotNull
    public PagedBatchRequestBuilder<K, V> add(Object id, @NotNull String method, @NotNull Object param) {
        requests.add(new Request(id, method, param));
        return this;
    }

    /**
     * Sets type of request keys.
     * The purpose of this method is providing static and runtime type safety of processing of batch responses
     *
     * @param keysClass type of keys
     * @param <NK>      type of keys
     * @return a new builder
     */
    public <NK> PagedBatchRequestBuilder<NK, V> keysType(@NotNull Class<NK> keysClass) {
        return new PagedBatchRequestBuilder<NK, V>(transport, mapper, requests, keysClass, returnType);
    }

    /**
     * Sets an expected response type of requests.
     * This method is preferred when requests have the same response type.
     *
     * @param valuesClass expected requests return type
     * @param <NV>        expected requests return type
     * @return a new builder
     */
    public <NV> PagedBatchRequestBuilder<K, NV> returnType(@NotNull Class<NV> valuesClass) {
        return new PagedBatchRequestBuilder<K, NV>(transport, mapper, requests, keysType, valuesClass);
    }

    /**
     * Validates, executes the request and process response
     *
     * @return map of responses by request ids
     */
    @NotNull
    public Map<K, V> execute() throws Exception {
        Map<K, V> allResults = new HashMap<>();
        JsonRpcClient client = new JsonRpcClient(transport);

        List<List<Request>> pages = Lists.partition(requests, getPageSize());
        for(List<Request> page : pages) {
            BatchRequestBuilder<K, V> batchRequest = client.createBatchRequest().keysType(keysType).returnType(returnType);
            for(Request request : page) {
                if(request.id instanceof String strReq) {
                    batchRequest.add(strReq, request.method, request.param);
                } else if(request.id instanceof Integer intReq) {
                    batchRequest.add(intReq, request.method, request.param);
                } else {
                    throw new IllegalArgumentException("Id of class " + request.id.getClass().getName() + " not supported");
                }
            }

            Map<K, V> pageResult = new RetryLogic<Map<K, V>>(MAX_RETRIES, RETRY_DELAY, List.of(IllegalStateException.class, IllegalArgumentException.class)).getResult(batchRequest::execute);
            allResults.putAll(pageResult);
        }

        return allResults;
    }

    private int getPageSize() {
        int pageSize = Config.get().getBatchPageSize();
        if(pageSize < 1) {
            pageSize = DEFAULT_PAGE_SIZE;
        }

        return pageSize;
    }

    /**
     * Creates a builder of a JSON-RPC batch request in initial state
     *
     * @return batch request builder
     */
    @NotNull
    public static PagedBatchRequestBuilder<?, ?> create(Transport transport) {
        return new PagedBatchRequestBuilder<Object, Object>(transport, new ObjectMapper());
    }

    private static record Request(Object id, String method, Object param) {}
}
