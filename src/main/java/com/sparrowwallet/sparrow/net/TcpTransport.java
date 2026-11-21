package com.sparrowwallet.sparrow.net;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.github.arteam.simplejsonrpc.core.annotation.JsonRpcMethod;
import com.github.arteam.simplejsonrpc.core.annotation.JsonRpcOptional;
import com.github.arteam.simplejsonrpc.core.annotation.JsonRpcParam;
import com.github.arteam.simplejsonrpc.server.JsonRpcServer;
import com.google.common.base.Splitter;
import com.google.common.net.HostAndPort;
import com.sparrowwallet.sparrow.io.Config;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.SocketFactory;
import javax.net.ssl.SSLHandshakeException;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class TcpTransport implements CloseableTransport, TimeoutCounter {
    private static final Logger log = LoggerFactory.getLogger(TcpTransport.class);
    private static final Logger wireLog = LoggerFactory.getLogger("electrum.wire");

    public static final int DEFAULT_MAX_TIMEOUT = 34;
    private static final int[] BASE_READ_TIMEOUT_SECS = {3, 8, 16, DEFAULT_MAX_TIMEOUT};
    private static final int[] TOR_READ_TIMEOUT_SECS = {5, 10, 20, DEFAULT_MAX_TIMEOUT};
    private static final int[] SLOW_READ_TIMEOUT_SECS = {34, 68, 124, 208};
    public static final long PER_REQUEST_READ_TIMEOUT_MILLIS = 50;
    public static final int SOCKET_READ_TIMEOUT_MILLIS = 5000;
    private static final int MAX_RESPONSE_CHARS = 256 * 1024 * 1024; //Above a default page of 1MB transactions as hex, and many times the largest valid transaction
    private static final Pattern ID_PATTERN = Pattern.compile("\"id\"\\s*:\\s*(\\d+)");
    private static final JsonFactory JSON_FACTORY = new JsonFactory();

    //The replies of a working proxy that could not reach the server, as SocksSocketImpl words them, which say nothing against the proxy itself
    private static final Set<String> SOCKS_DESTINATION_FAILURES = Set.of("SOCKS server general failure", "SOCKS: Network unreachable", "SOCKS: Host unreachable",
            "SOCKS: Connection refused", "SOCKS: TTL expired", "SOCKS server couldn't reach destination");

    private static final AtomicLong deliveredResponses = new AtomicLong(); //Counts the responses delivered to callers ordering notifications with responses
    private static final ThreadLocal<Long> lastResponseSequence = new ThreadLocal<>(); //Sequence of the response this thread's last RPC received

    protected final HostAndPort server;
    protected final SocketFactory socketFactory;
    protected final int[] readTimeouts;

    protected Socket socket;

    private String response;

    private final CountDownLatch readReadySignal = new CountDownLatch(1);

    private final ReentrantLock readLock = new ReentrantLock();
    private final Condition readingCondition = readLock.newCondition();

    private final ReentrantLock clientRequestLock = new ReentrantLock();
    private volatile boolean running = false;
    private volatile boolean reading = true;
    private volatile boolean closed = false;
    private boolean firstRead = true;
    private int readTimeoutIndex;
    private volatile int timeoutCount;
    private int requestIdCount = 1;

    private final char[] readBuffer = new char[8192];
    private int readBufferPos;
    private int readBufferEnd;
    int maxResponseChars = MAX_RESPONSE_CHARS;

    private final JsonRpcServer jsonRpcServer = new JsonRpcServer();
    private final SubscriptionService subscriptionService = new SubscriptionService();

    private Exception lastException;

    public TcpTransport(HostAndPort server) {
        this(server, null);
    }

    public TcpTransport(HostAndPort server, HostAndPort proxy) {
        this.server = server;
        this.socketFactory = (proxy == null ? SocketFactory.getDefault() : new ProxySocketFactory(proxy));

        int[] baseTimeouts;
        if(Config.get().getServerType() == ServerType.BITCOIN_CORE && Protocol.isOnionAddress(Config.get().getCoreServer())) {
            baseTimeouts = SLOW_READ_TIMEOUT_SECS;
        } else if(proxy != null) {
            baseTimeouts = TOR_READ_TIMEOUT_SECS;
        } else {
            baseTimeouts = BASE_READ_TIMEOUT_SECS;
        }
        int[] timeouts = Arrays.copyOf(baseTimeouts, baseTimeouts.length);
        if(Config.get().getMaxServerTimeout() > timeouts[timeouts.length - 1]) {
            timeouts[timeouts.length - 1] = Config.get().getMaxServerTimeout();
        }
        this.readTimeouts = timeouts;
    }

    @Override
    public @NotNull String pass(@NotNull String request) throws IOException {
        Set<String> sentIdSet = extractIdSet(request);
        clientRequestLock.lock();
        try {
            //Count number of requests in batched query to increase read timeout appropriately
            requestIdCount = Splitter.on("\"id\"").splitToList(request).size() - 1;
            writeRequest(request);

            String recv;
            Set<String> recvIdSet;
            do {
                recv = readResponse();
                recvIdSet = extractIdSet(recv);
                if(!sentIdSet.equals(recvIdSet)) {
                    log.info("Discarding stale response with ids " + recvIdSet + " (expected " + sentIdSet + ")");
                }
            } while(!sentIdSet.equals(recvIdSet));

            return recv;
        } finally {
            clientRequestLock.unlock();
        }
    }

    protected void writeRequest(String request) throws IOException {
        if(log.isTraceEnabled()) {
            log.trace("Sending to electrum server at " + server + ": " + request);
        }

        if(socket == null) {
            throw new IllegalStateException("Socket connection has not been established.");
        }

        wireLog.info("> " + request);

        PrintWriter out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8)));
        out.println(request);
        out.flush();
    }

    private String readResponse() throws IOException {
        if(firstRead) {
            try {
                //Ensure read thread has started
                if(!readReadySignal.await(2, TimeUnit.SECONDS)) {
                    throw new IOException("Read thread did not start");
                }
            } catch(InterruptedException e) {
                throw new IOException("Read ready await interrupted");
            }
        }

        //This deadline must bound the entire wait for a response, not just acquiring readLock below. The read thread releases
        //readLock while blocked on the socket, so tryLock() returns almost immediately and the real wait is the await further down
        if(readTimeoutIndex == readTimeouts.length - 1) {
            readTimeoutIndex--;
        }

        long startNanos = System.nanoTime();
        long deadlineNanos = getDeadlineNanos(startNanos);

        try {
            if(!readLock.tryLock(deadlineNanos - System.nanoTime(), TimeUnit.NANOSECONDS)) {
                throw timeout();
            }
        } catch(InterruptedException e) {
            throw new IOException("Read thread interrupted");
        }

        try {
            if(firstRead) {
                readingCondition.signal();
                firstRead = false;
            }

            while(reading && running) {
                long remainingNanos = deadlineNanos - System.nanoTime();
                if(remainingNanos <= 0) {
                    if(readTimeoutIndex < readTimeouts.length - 1) {
                        extendReadTimeout();
                        deadlineNanos = getDeadlineNanos(startNanos);
                        continue;
                    }
                    throw timeout();
                }

                try {
                    readingCondition.awaitNanos(remainingNanos);
                } catch(InterruptedException e) {
                    //Restore interrupt status and break
                    Thread.currentThread().interrupt();
                    break;
                }
            }

            if(lastException != null) {
                throw new IOException("Error reading response: " + lastException.getMessage(), lastException);
            }

            if(!running) {
                throw new IOException("Transport closed");
            }

            lastResponseSequence.set(deliveredResponses.get());
            reading = true;

            readingCondition.signal();
            return response;
        } finally {
            readLock.unlock();
        }
    }

    private long getDeadlineNanos(long startNanos) {
        return startNanos + TimeUnit.MILLISECONDS.toNanos((readTimeouts[readTimeoutIndex] * 1000L) + (requestIdCount * PER_REQUEST_READ_TIMEOUT_MILLIS));
    }

    //Keep waiting for the response rather than sending the request again. A server that answers requests in turn, as electrs does, still has the request
    //queued, so a resend only arrives behind it and doubles the work. The longer timeout is kept for later requests to this server
    private void extendReadTimeout() {
        readTimeoutIndex++;
        log.info("No response from server in " + readTimeouts[readTimeoutIndex - 1] + " secs, waiting up to " + readTimeouts[readTimeoutIndex] + " secs");
    }

    //Note the connection is deliberately left open here - callers retry over the same transport with a halved batch page size,
    //discarding the stale response that a slow server eventually delivers
    private IOException timeout() {
        timeoutCount++;
        log.warn("No response from server in " + readTimeouts[readTimeoutIndex] + " secs");
        return new IOException("No response from server");
    }

    public void readInputLoop() throws ServerException {
        Reader in;
        try {
            in = new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8);
        } catch(IOException e) {
            if(!closed) {
                log.error("Error opening socket inputstream", e);
            }
            if(running) {
                signalException(e);
            }
            return;
        }

        //Wait for first RPC request before starting to read. The lock must be acquired before
        //signaling readiness so readResponse() blocks until we reach the atomic await/unlock.
        readLock.lock();
        try {
            readReadySignal.countDown();
            if(running) {
                readingCondition.await();
            }
        } catch(InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        } finally {
            readLock.unlock();
        }

        while(running) {
            try {
                String received = readInputStream(in);
                wireLog.info("< " + received);
                MessageType messageType = getMessageType(received);
                if(messageType == MessageType.SUBSCRIPTION_NOTIFICATION) {
                    jsonRpcServer.handle(received, subscriptionService);
                } else if(messageType == MessageType.OTHER_NOTIFICATION) {
                    warnUnhandledNotification();
                } else {
                    deliverResponse(received);
                }
            } catch(InterruptedException e) {
                //Restore interrupt status and continue
                Thread.currentThread().interrupt();
            } catch(Exception e) {
                if(!closed) {
                    log.trace("Connection error while reading", e);
                }
                if(running) {
                    //Allow this thread to terminate as we will need to reconnect with a new transport anyway
                    signalException(e);
                }
            }
        }
    }

    private void deliverResponse(String received) throws InterruptedException {
        readLock.lock();
        try {
            response = received;
            deliveredResponses.incrementAndGet();
            reading = false;
            readingCondition.signal();
            while(!reading && running) {
                readingCondition.await();
            }
        } finally {
            readLock.unlock();
        }
    }

    private void signalException(Exception e) {
        readLock.lock();
        try {
            lastException = e;
            //Set before the waiting caller wakes, so it sees the transport disconnected along with the exception
            running = false;
            reading = false;
            readingCondition.signal();
        } finally {
            readLock.unlock();
        }
    }

    protected String readInputStream(Reader in) throws IOException {
        String response = readLine(in);

        if(response == null) {
            throw new IOException("Could not connect to server" + (Config.get().hasServer() ? " at " + Config.get().getServer().getUrl() : ""));
        }

        return response;
    }

    //Unlike BufferedReader.readLine(), keeps the start of a line when a socket read times out partway through it
    private String readLine(Reader in) throws IOException {
        StringBuilder line = new StringBuilder();
        while(!socket.isClosed()) {
            if(readBufferPos == readBufferEnd) {
                int read;
                try {
                    read = in.read(readBuffer, 0, readBuffer.length);
                } catch(SocketTimeoutException e) {
                    continue;
                }

                if(read < 0) {
                    return line.isEmpty() ? null : line.toString();
                }

                readBufferPos = 0;
                readBufferEnd = read;
            }

            for(int i = readBufferPos; i < readBufferEnd; i++) {
                if(readBuffer[i] == '\n') {
                    line.append(readBuffer, readBufferPos, i - readBufferPos);
                    readBufferPos = i + 1;
                    if(!line.isEmpty() && line.charAt(line.length() - 1) == '\r') {
                        line.setLength(line.length() - 1);
                    }

                    return line.toString();
                }
            }

            line.append(readBuffer, readBufferPos, readBufferEnd - readBufferPos);
            readBufferPos = readBufferEnd;
            if(line.length() > maxResponseChars) {
                throw new IOException("Server response exceeded " + maxResponseChars + " characters");
            }
        }

        return null;
    }

    public void connect() throws ServerException {
        try {
            createSocket();
            log.debug("Created " + socket);
            socket.setSoTimeout(SOCKET_READ_TIMEOUT_MILLIS);
            running = true;
        } catch(SSLHandshakeException e) {
            throw new TlsServerException(server, e);
        } catch(IOException e) {
            if(e.getStackTrace().length > 0 && e.getStackTrace()[0].getClassName().contains("SocksSocketImpl") && !SOCKS_DESTINATION_FAILURES.contains(e.getMessage())) {
                throw new ProxyServerException(e);
            }

            throw new ServerException(e);
        }
    }

    public boolean isConnected() {
        return socket != null && running && !closed;
    }

    protected void createSocket() throws IOException {
        socket = socketFactory.createSocket();
        socket.connect(socketFactory instanceof ProxySocketFactory ?
                InetSocketAddress.createUnresolved(server.getHost(), server.getPortOrDefault(getDefaultPort())) :
                new InetSocketAddress(server.getHost(), server.getPortOrDefault(getDefaultPort())));
    }

    protected int getDefaultPort() {
        return Protocol.TCP.getDefaultPort();
    }

    public boolean isClosed() {
        return closed;
    }

    @Override
    public void close() throws IOException {
        running = false;
        closed = true;

        readLock.lock();
        try {
            readingCondition.signalAll();
        } finally {
            readLock.unlock();
        }

        if(socket != null) {
            socket.close();
        }
    }

    public boolean isRequestPending() {
        return clientRequestLock.isLocked();
    }

    @Override
    public int getTimeoutCount() {
        return timeoutCount;
    }

    static long getDeliveredResponses() {
        return deliveredResponses.get();
    }

    static long getLastResponseSequence() {
        Long sequence = lastResponseSequence.get();
        return sequence == null ? 0L : sequence;
    }

    //The params of each notification the subscription service handles, by name, and those of them it requires
    private record SubscriptionParams(Set<String> names, Set<String> required) {}

    private static final Map<String, SubscriptionParams> SUBSCRIPTION_PARAMS = Arrays.stream(SubscriptionService.class.getMethods())
            .filter(method -> method.isAnnotationPresent(JsonRpcMethod.class))
            .collect(Collectors.toUnmodifiableMap(method -> method.getAnnotation(JsonRpcMethod.class).value(), method -> new SubscriptionParams(
                    Arrays.stream(method.getParameters()).map(parameter -> parameter.getAnnotation(JsonRpcParam.class).value()).collect(Collectors.toUnmodifiableSet()),
                    Arrays.stream(method.getParameters()).filter(parameter -> !parameter.isAnnotationPresent(JsonRpcOptional.class))
                            .map(parameter -> parameter.getAnnotation(JsonRpcParam.class).value()).collect(Collectors.toUnmodifiableSet()))));

    private static final long UNHANDLED_NOTIFICATION_WARNING_MILLIS = TimeUnit.MINUTES.toMillis(1);
    private static final AtomicLong lastUnhandledNotificationWarning = new AtomicLong();

    enum MessageType { RESPONSE, SUBSCRIPTION_NOTIFICATION, OTHER_NOTIFICATION }

    //A notification names a method, and is passed on only where the subscription service handles that method with these params
    static MessageType getMessageType(String json) {
        try(JsonParser parser = JSON_FACTORY.createParser(json)) {
            if(parser.nextToken() != JsonToken.START_OBJECT) {
                return MessageType.RESPONSE;
            }

            String method = null;
            int paramCount = -1;
            Set<String> paramNames = null;
            while(parser.nextToken() == JsonToken.FIELD_NAME) {
                String field = parser.currentName();
                JsonToken value = parser.nextToken();
                if("method".equals(field) && value == JsonToken.VALUE_STRING) {
                    method = parser.getText();
                } else if("params".equals(field) && value == JsonToken.START_ARRAY) {
                    paramCount = 0;
                    while(parser.nextToken() != JsonToken.END_ARRAY) {
                        parser.skipChildren();
                        paramCount++;
                    }
                } else if("params".equals(field) && value == JsonToken.START_OBJECT) {
                    paramNames = new LinkedHashSet<>();
                    while(parser.nextToken() == JsonToken.FIELD_NAME) {
                        paramNames.add(parser.currentName());
                        parser.nextToken();
                        parser.skipChildren();
                    }
                } else {
                    parser.skipChildren();
                }
            }

            if(method == null) {
                return MessageType.RESPONSE;
            }

            SubscriptionParams params = SUBSCRIPTION_PARAMS.get(method);
            boolean handled = params != null && (paramNames != null ? params.names().containsAll(paramNames) && paramNames.containsAll(params.required())
                    : paramCount >= params.required().size() && paramCount <= params.names().size());
            return handled ? MessageType.SUBSCRIPTION_NOTIFICATION : MessageType.OTHER_NOTIFICATION;
        } catch(Exception e) {
            log.warn("Could not parse JSON-RPC message from server: " + e.getMessage());
            return MessageType.RESPONSE;
        }
    }

    private static void warnUnhandledNotification() {
        long now = System.currentTimeMillis();
        long last = lastUnhandledNotificationWarning.get();
        if(now - last >= UNHANDLED_NOTIFICATION_WARNING_MILLIS && lastUnhandledNotificationWarning.compareAndSet(last, now)) {
            log.warn("Ignoring notifications from server that do not match a subscription");
        }
    }

    private static Set<String> extractIdSet(String json) {
        if(json == null || json.isEmpty()) {
            return Collections.emptySet();
        }
        Matcher m = ID_PATTERN.matcher(json);
        Set<String> ids = new LinkedHashSet<>();
        while(m.find()) {
            ids.add(m.group(1));
        }
        return ids;
    }
}
