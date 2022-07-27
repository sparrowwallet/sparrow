package com.sparrowwallet.sparrow.net;

import com.github.arteam.simplejsonrpc.client.Transport;

import java.io.Closeable;

public interface CloseableTransport extends Transport, Closeable {
    void connect() throws ServerException;
    boolean isConnected();
    boolean isClosed();
}
