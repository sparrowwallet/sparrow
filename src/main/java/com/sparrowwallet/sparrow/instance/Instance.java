package com.sparrowwallet.sparrow.instance;

import com.sparrowwallet.sparrow.io.Storage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Set;

public abstract class Instance {
    private static final Logger log = LoggerFactory.getLogger(Instance.class);
    private static final String LINK_ENV_PROPERTY = "SPARROW_NO_LOCK_FILE_LINK";

    public final String applicationId;
    private final boolean autoExit;

    private Selector selector;
    private ServerSocketChannel serverChannel;

    public Instance(final String applicationId) {
        this(applicationId, true);
    }

    public Instance(final String applicationId, final boolean autoExit) {
        this.applicationId = applicationId;
        this.autoExit = autoExit;
    }

    /**
     * Try to obtain lock. If not possible, send data to first instance.
     *
     * @throws InstanceException throws InstanceException if it is unable to start a server or connect to server
     */
    public void acquireLock(boolean findExisting) throws InstanceException {
        Path lockFile = getLockFile(findExisting);

        if(!Files.exists(lockFile)) {
            startServer(lockFile);
        } else {
            doClient(lockFile);
        }
    }

    private void startServer(Path lockFile) throws InstanceException {
        try {
            selector = Selector.open();
            UnixDomainSocketAddress socketAddress = UnixDomainSocketAddress.of(lockFile);
            serverChannel = ServerSocketChannel.open(StandardProtocolFamily.UNIX);
            serverChannel.bind(socketAddress);
            serverChannel.configureBlocking(false);
            serverChannel.register(selector, SelectionKey.OP_ACCEPT);
            lockFile.toFile().deleteOnExit();
        } catch(Exception e) {
            throw new InstanceException("Could not open UNIX socket lock file for instance at " + lockFile.toAbsolutePath(), e);
        }

        Thread thread = new Thread(() -> {
            while(true) {
                try {
                    selector.select();
                    Set<SelectionKey> selectedKeys = selector.selectedKeys();
                    Iterator<SelectionKey> iter = selectedKeys.iterator();
                    while(iter.hasNext()) {
                        SelectionKey key = iter.next();
                        if(key.isAcceptable()) {
                            SocketChannel client = serverChannel.accept();
                            client.configureBlocking(false);
                            client.register(selector, SelectionKey.OP_READ);
                        }
                        if(key.isReadable()) {
                            try(SocketChannel clientChannel = (SocketChannel)key.channel()) {
                                String message = readMessage(clientChannel);
                                clientChannel.write(ByteBuffer.wrap(applicationId.getBytes(StandardCharsets.UTF_8)));
                                receiveMessage(message);
                            }
                        }
                        iter.remove();
                    }
                } catch(SocketException e) {
                    if(serverChannel.isOpen()) {
                        handleException(new InstanceException(e));
                    }
                } catch(Exception e) {
                    handleException(new InstanceException(e));
                }
            }
        });

        thread.setDaemon(true);
        thread.setName("SparrowInstanceListener");
        thread.start();

        createSymlink(lockFile);
    }

    private void doClient(Path lockFile) throws InstanceException {
        try(SocketChannel client = SocketChannel.open(UnixDomainSocketAddress.of(lockFile))) {
            String message = sendMessage();
            client.write(ByteBuffer.wrap(message.getBytes(StandardCharsets.UTF_8)));
            client.shutdownOutput();

            String response = readMessage(client);
            if(response.equals(applicationId) && autoExit) {
                beforeExit();
                System.exit(0);
            }
        } catch(ConnectException e) {
            try {
                Files.deleteIfExists(lockFile);
                startServer(lockFile);
            } catch(Exception ex) {
                throw new InstanceException("Could not delete lock file from previous instance", e);
            }
        } catch(Exception e) {
            throw new InstanceException("Could not open client connection to existing instance", e);
        }
    }

    private static String readMessage(SocketChannel clientChannel) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(1024);
        StringBuilder messageBuilder = new StringBuilder();
        while(clientChannel.read(buffer) != -1) {
            buffer.flip();
            messageBuilder.append(new String(buffer.array(), 0, buffer.limit()));
            buffer.clear();
        }

        return messageBuilder.toString();
    }

    private Path getLockFile(boolean findExisting) {
        if(findExisting) {
            Path pointer = getUserLockFilePointer();
            try {
                if(pointer != null && Files.exists(pointer)) {
                    if(Files.isSymbolicLink(pointer)) {
                        return Files.readSymbolicLink(pointer);
                    } else {
                        Path lockFile = Path.of(Files.readString(pointer, StandardCharsets.UTF_8));
                        if(Files.exists(lockFile)) {
                            return lockFile;
                        }
                    }
                }
            } catch(IOException e) {
                log.warn("Could not find lock file at " + pointer.toAbsolutePath());
            } catch(Exception e) {
                //ignore
            }
        }

        return Storage.getSparrowDir().toPath().resolve(applicationId + ".lock");
    }

    private void createSymlink(Path lockFile) {
        Path pointer = getUserLockFilePointer();
        try {
            if(pointer != null && !Files.exists(pointer, LinkOption.NOFOLLOW_LINKS)) {
                Files.createSymbolicLink(pointer, lockFile);
                pointer.toFile().deleteOnExit();
            }
        } catch(IOException e) {
            log.debug("Could not create symlink " + pointer.toAbsolutePath() + " to lockFile at " + lockFile.toAbsolutePath() + ", writing as normal file", e);

            try {
                Files.writeString(pointer, lockFile.toAbsolutePath().toString(), StandardCharsets.UTF_8);
                pointer.toFile().deleteOnExit();
            } catch(IOException ex) {
                log.warn("Could not create pointer " + pointer.toAbsolutePath() + " to lockFile at " + lockFile.toAbsolutePath(), ex);
            }
        } catch(Exception e) {
            //ignore
        }
    }

    private Path getUserLockFilePointer() {
        if(Boolean.parseBoolean(System.getenv(LINK_ENV_PROPERTY))) {
            return null;
        }

        try {
            File sparrowHome = Storage.getSparrowHome(true);
            if(!sparrowHome.exists()) {
                Storage.createOwnerOnlyDirectory(sparrowHome);
            }

            return sparrowHome.toPath().resolve(applicationId + ".default");
        } catch(Exception e) {
            return null;
        }
    }

    /**
     * Free the lock if possible. This is only required to be called from the first instance.
     *
     * @throws InstanceException throws InstanceException if it is unable to stop the server or release file lock
     */
    public void freeLock() throws InstanceException {
        try {
            if(serverChannel != null && serverChannel.isOpen()) {
                serverChannel.close();
            }
            if(getUserLockFilePointer() != null) {
                Files.deleteIfExists(getUserLockFilePointer());
            }
            Files.deleteIfExists(getLockFile(false));
        } catch(Exception e) {
            throw new InstanceException(e);
        }
    }

    /**
     * Method used in first instance to receive messages from subsequent instances.<br><br>
     *
     * This method is not synchronized.
     *
     * @param message message received by first instance from subsequent instances
     */
    protected abstract void receiveMessage(String message);

    /**
     * Method used in subsequent instances to send message to first instance.<br><br>
     *
     * It is not recommended to perform blocking (long running) tasks here. Use <code>beforeExit()</code> method instead.<br>
     * One exception to this rule is if you intend to perform some user interaction before sending the message.<br><br>
     *
     * This method is not synchronized.
     *
     * @return message sent from subsequent instances
     */
    protected abstract String sendMessage();

    /**
     * Method to receive and handle exceptions occurring while first instance is listening for subsequent instances.<br><br>
     *
     * By default prints stack trace of all exceptions. Override this method to handle exceptions explicitly.<br><br>
     *
     * This method is not synchronized.
     *
     * @param exception exception occurring while first instance is listening for subsequent instances
     */
    protected void handleException(Exception exception) {
        log.error("Error listening for instances", exception);
    }

    /**
     * This method is called before exiting from subsequent instances.<br><br>
     *
     * Override this method to perform blocking tasks before exiting from subsequent instances.<br>
     * This method is not invoked if auto exit is turned off.<br><br>
     *
     * This method is not synchronized.
     */
    protected void beforeExit() {}
}
