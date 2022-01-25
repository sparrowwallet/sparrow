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
import java.util.concurrent.atomic.AtomicLong;

import static com.sparrowwallet.sparrow.net.BatchedElectrumServerRpc.DEFAULT_MAX_ATTEMPTS;
import static com.sparrowwallet.sparrow.net.BatchedElectrumServerRpc.RETRY_DELAY_SECS;

public class PagedBatchRequestBuilder<K, V> extends AbstractBuilder {
    public static final int DEFAULT_PAGE_SIZE = 500;

    private final AtomicLong counter;

    @NotNull
    private final List<Request<K>> requests;

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
    public PagedBatchRequestBuilder(@NotNull Transport transport, @NotNull ObjectMapper mapper, AtomicLong counter) {
        this(transport, mapper, new ArrayList<Request<K>>(), null, null, counter);
    }

    public PagedBatchRequestBuilder(@NotNull Transport transport, @NotNull ObjectMapper mapper,
                                    @NotNull List<Request<K>> requests,
                                    @Nullable Class<K> keysType, @Nullable Class<V> returnType,
                                    @Nullable AtomicLong counter) {
        super(transport, mapper);
        this.requests = requests;
        this.keysType = keysType;
        this.returnType = returnType;
        this.counter = counter;
    }

    /**
     * Adds a new request without specifying a return type
     *
     * @param id     request id as a text value
     * @param method request method
     * @param params request params
     * @return the current builder
     */
    @NotNull
    public PagedBatchRequestBuilder<K, V> add(K id, @NotNull String method, @NotNull Object... params) {
        requests.add(new Request<K>(id, counter == null ? null : counter.incrementAndGet(), method, params));
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
        return new PagedBatchRequestBuilder<NK, V>(transport, mapper, new ArrayList<Request<NK>>(), keysClass, returnType, counter);
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
        return new PagedBatchRequestBuilder<K, NV>(transport, mapper, requests, keysType, valuesClass, counter);
    }

    public Map<K, V> execute() throws Exception {
        return execute(DEFAULT_MAX_ATTEMPTS);
    }

    /**
     * Validates, executes the request and process response
     *
     * @param maxAttempts number of times to try the request
     * @return map of responses by request ids
     */
    @NotNull
    public Map<K, V> execute(int maxAttempts) throws Exception {
        Map<K, V> allResults = new HashMap<>();
        JsonRpcClient client = new JsonRpcClient(transport);

        List<List<Request<K>>> pages = Lists.partition(requests, getPageSize());
        for(List<Request<K>> page : pages) {
            if(counter != null) {
                Map<Long, K> counterIdMap = new HashMap<>();
                BatchRequestBuilder<Long, V> batchRequest = client.createBatchRequest().keysType(Long.class).returnType(returnType);
                for(Request<K> request : page) {
                    counterIdMap.put(request.counterId, request.id);
                    batchRequest.add(request.counterId, request.method, request.params);
                }

                Map<Long, V> pageResult = new RetryLogic<Map<Long, V>>(maxAttempts, RETRY_DELAY_SECS, List.of(IllegalStateException.class, IllegalArgumentException.class)).getResult(batchRequest::execute);
                for(Map.Entry<Long, V> pageEntry : pageResult.entrySet()) {
                    allResults.put(counterIdMap.get(pageEntry.getKey()), pageEntry.getValue());
                }
            } else {
                BatchRequestBuilder<K, V> batchRequest = client.createBatchRequest().keysType(keysType).returnType(returnType);
                for(Request<K> request : page) {
                    if(request.id instanceof String strReq) {
                        batchRequest.add(strReq, request.method, request.params);
                    } else if(request.id instanceof Integer intReq) {
                        batchRequest.add(intReq, request.method, request.params);
                    } else {
                        throw new IllegalArgumentException("Id of class " + request.id.getClass().getName() + " not supported");
                    }
                }

                Map<K, V> pageResult = new RetryLogic<Map<K, V>>(maxAttempts, RETRY_DELAY_SECS, List.of(IllegalStateException.class, IllegalArgumentException.class)).getResult(batchRequest::execute);
                allResults.putAll(pageResult);
            }
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
        return new PagedBatchRequestBuilder<Object, Object>(transport, new ObjectMapper(), null);
    }

    /**
     * Creates a builder of a JSON-RPC batch request in initial state with a counter for request ids
     *
     * @return batch request builder
     */
    @NotNull
    public static PagedBatchRequestBuilder<?, ?> create(Transport transport, AtomicLong counter) {
        return new PagedBatchRequestBuilder<Object, Object>(transport, new ObjectMapper(), counter);
    }

    private static record Request<K>(K id, Long counterId, String method, Object[] params) {}
}
