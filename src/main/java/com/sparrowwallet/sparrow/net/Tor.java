package com.sparrowwallet.sparrow.net;

import com.google.common.net.HostAndPort;
import com.sparrowwallet.drongo.IOUtils;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.event.TorStatusEvent;
import com.sparrowwallet.sparrow.io.Storage;
import io.matthewnelson.kmp.tor.resource.exec.tor.ResourceLoaderTorExec;
import io.matthewnelson.kmp.tor.runtime.Action;
import io.matthewnelson.kmp.tor.runtime.RuntimeEvent;
import io.matthewnelson.kmp.tor.runtime.TorListeners;
import io.matthewnelson.kmp.tor.runtime.TorRuntime;
import io.matthewnelson.kmp.tor.runtime.core.OnEvent;
import io.matthewnelson.kmp.tor.runtime.core.OnFailure;
import io.matthewnelson.kmp.tor.runtime.core.OnSuccess;
import io.matthewnelson.kmp.tor.runtime.core.TorEvent;
import io.matthewnelson.kmp.tor.runtime.core.ctrl.TorCmd;
import io.matthewnelson.kmp.tor.runtime.core.net.IPSocketAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.File;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.nio.file.Path;

public class Tor implements Closeable {
    private static final Logger log = LoggerFactory.getLogger(Tor.class);

    public static final String TOR_DIR = "tor";
    public static final String WORK_DIR = "work";
    public static final String CACHE_DIR = "cache";
    public static final String OLD_INSTALL_DIR = ".kmptor";
    public static final String TOR_ADDRESS_SUFFIX = ".onion";

    private static Tor tor;

    private final TorRuntime instance;
    private IPSocketAddress socksAddress;

    public Tor(OnEvent<TorListeners> listener) {
        Path path = Path.of(Storage.getSparrowHome().getAbsolutePath()).resolve(TOR_DIR);
        File oldInstallDir = path.resolve(WORK_DIR).resolve(OLD_INSTALL_DIR).toFile();
        if(oldInstallDir.exists()) {
            IOUtils.deleteDirectory(oldInstallDir);
        }

        TorRuntime.Environment env = TorRuntime.Environment.Builder(
                path.resolve(WORK_DIR).toFile(),
                path.resolve(CACHE_DIR).toFile(),
                ResourceLoaderTorExec::getOrCreate
        );

        env.debug = true;

        instance = TorRuntime.Builder(env, b -> {
            RuntimeEvent.entries().forEach(event -> {
                switch(event) {
                    case RuntimeEvent.LOG.ERROR _ -> b.observerStatic(event, OnEvent.Executor.Immediate.INSTANCE, data -> {
                        log.error(data.toString());
                        EventManager.get().post(new TorStatusEvent(data.toString()));
                    });
                    case RuntimeEvent.LOG.WARN _ -> b.observerStatic(event, OnEvent.Executor.Immediate.INSTANCE, data -> {
                        log.warn(data.toString());
                        EventManager.get().post(new TorStatusEvent(data.toString()));
                    });
                    case RuntimeEvent.LOG.INFO _ -> b.observerStatic(event, OnEvent.Executor.Immediate.INSTANCE, data -> {
                        log.info(data.toString());
                        EventManager.get().post(new TorStatusEvent(data.toString()));
                    });
                    case RuntimeEvent.LOG.DEBUG _ -> b.observerStatic(event, OnEvent.Executor.Immediate.INSTANCE, data -> {
                        log.debug(data.toString());
                        EventManager.get().post(new TorStatusEvent(data.toString()));
                    });
                    case null, default -> b.observerStatic(event, OnEvent.Executor.Immediate.INSTANCE, data -> {
                        log.trace(data.toString());
                    });
                }
            });

            b.observerStatic(RuntimeEvent.LISTENERS.INSTANCE, new TorSocksClient());
            b.observerStatic(RuntimeEvent.LISTENERS.INSTANCE, listener);

            b.required(TorEvent.ERR.INSTANCE);
            b.required(TorEvent.WARN.INSTANCE);
        });
    }

    public static Tor getDefault() {
        return Tor.tor;
    }

    public static void setDefault(Tor tor) {
        Tor.tor = tor;
    }

    public TorRuntime getTorRuntime() {
        return instance;
    }

    public Proxy getProxy() {
        if(socksAddress == null) {
            throw new IllegalStateException("Tor socks proxy is not yet instantiated");
        }

        return new Proxy(Proxy.Type.SOCKS, new InetSocketAddress(socksAddress.address.value, socksAddress.port.value));
    }

    public HostAndPort getProxyHostAndPort() {
        return HostAndPort.fromParts(socksAddress.address.value, socksAddress.port.value);
    }

    public void changeIdentity() {
        if(instance != null) {
            instance.enqueue(TorCmd.Signal.NewNym.INSTANCE, throwable -> {
                log.warn("Failed to signal newnym", throwable);
            }, _ -> {
                log.info("Signalled newnym for new Tor circuit");
            });
        }
    }

    @Override
    public void close() {
        if(instance != null) {
            instance.enqueue(Action.StopDaemon, OnFailure.noOp(), OnSuccess.noOp());
        }
    }

    private class TorSocksClient implements OnEvent<TorListeners> {
        @Override
        public void invoke(TorListeners torListeners) {
            socksAddress = torListeners.socks.isEmpty() ? null : torListeners.socks.iterator().next();
        }
    }
}
