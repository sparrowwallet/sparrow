package com.sparrowwallet.sparrow.net.cormorant.electrum;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

public class RequestHandlerTest {
    private static final String SCRIPT_HASH = "a".repeat(64);

    @Test
    public void testNotificationWaitsForResponseInProgress() throws Exception {
        CountDownLatch readerStarted = new CountDownLatch(1);
        CountDownLatch endOfInput = new CountDownLatch(1);
        CountDownLatch firstChunkWritten = new CountDownLatch(1);
        CountDownLatch releaseFirstChunk = new CountDownLatch(1);
        ByteArrayOutputStream written = new ByteArrayOutputStream();

        InputStream input = new InputStream() {
            @Override
            public int read() throws IOException {
                return read(new byte[1], 0, 1);
            }

            @Override
            public int read(byte[] b, int off, int len) throws IOException {
                readerStarted.countDown();
                try {
                    endOfInput.await(10, TimeUnit.SECONDS);
                } catch(InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return -1;
            }
        };

        //Holds the first write, which is the first chunk of a response too long for the writer's buffer, until released
        OutputStream output = new OutputStream() {
            private boolean paused;

            @Override
            public void write(int b) throws IOException {
                write(new byte[] {(byte)b}, 0, 1);
            }

            @Override
            public void write(byte[] b, int off, int len) throws IOException {
                boolean pause;
                synchronized(written) {
                    written.write(b, off, len);
                    pause = !paused;
                    paused = true;
                }
                if(pause) {
                    firstChunkWritten.countDown();
                    try {
                        releaseFirstChunk.await(10, TimeUnit.SECONDS);
                    } catch(InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        };

        Socket socket = new Socket() {
            @Override
            public InputStream getInputStream() {
                return input;
            }

            @Override
            public OutputStream getOutputStream() {
                return output;
            }
        };

        RequestHandler requestHandler = new RequestHandler(socket, null, 0);
        requestHandler.subscribeScriptHash(SCRIPT_HASH);
        String response = "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":\"" + "0".repeat(20000) + "\"}";

        ExecutorService executor = Executors.newFixedThreadPool(3);
        try {
            Future<?> handler = executor.submit(requestHandler);
            assertTrue(readerStarted.await(5, TimeUnit.SECONDS), "The handler must reach its read loop");

            Future<?> responseSent = executor.submit(() -> requestHandler.send(response));
            assertTrue(firstChunkWritten.await(5, TimeUnit.SECONDS), "The response must begin writing");

            //The polling thread posts status notifications while a response is being written
            Future<?> notificationSent = executor.submit(() -> requestHandler.scriptHashStatus(new ScriptHashStatus(SCRIPT_HASH, "status")));
            assertThrows(TimeoutException.class, () -> notificationSent.get(200, TimeUnit.MILLISECONDS), "A notification must wait for the response being written");

            releaseFirstChunk.countDown();
            responseSent.get(5, TimeUnit.SECONDS);
            notificationSent.get(5, TimeUnit.SECONDS);

            endOfInput.countDown();
            handler.get(5, TimeUnit.SECONDS);
            assertTrue(socket.isClosed(), "The handler must close the client socket when it exits");

            List<String> lines = written.toString(StandardCharsets.UTF_8).lines().toList();
            assertEquals(2, lines.size(), "The response and the notification must each arrive as one whole line");
            assertEquals(response, lines.get(0));
            assertTrue(lines.get(1).contains("\"blockchain.scripthash.subscribe\""));
            assertTrue(lines.get(1).contains(SCRIPT_HASH));
        } finally {
            releaseFirstChunk.countDown();
            endOfInput.countDown();
            executor.shutdownNow();
        }
    }
}
