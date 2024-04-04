package com.sparrowwallet.sparrow.net;

import com.google.common.net.HostAndPort;
import com.sparrowwallet.drongo.Utils;
import com.sparrowwallet.sparrow.AppServices;
import io.matthewnelson.kmp.tor.controller.common.control.usecase.TorControlSignal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TorUtils {
    private static final Logger log = LoggerFactory.getLogger(TorUtils.class);
    private static final Pattern TOR_OK = Pattern.compile("^2\\d{2}[ -]OK$");
    private static final Pattern TOR_AUTH_METHODS = Pattern.compile("^2\\d{2}[ -]AUTH METHODS=(\\S+)\\s?(COOKIEFILE=\"?(.+?)\"?)?$");

    public static void changeIdentity(HostAndPort proxy) {
        if(AppServices.isTorRunning()) {
            Tor.getDefault().getTorManager().signal(TorControlSignal.Signal.NewNym, throwable -> {
                log.warn("Failed to signal newnym");
            }, successEvent -> {
                log.info("Signalled newnym for new Tor circuit");
            });
        } else {
            HostAndPort control = HostAndPort.fromParts(proxy.getHost(), proxy.getPort() + 1);
            try(Socket socket = new Socket(control.getHost(), control.getPort())) {
                socket.setSoTimeout(1500);
                if(authenticate(socket)) {
                    writeNewNym(socket);
                }
            } catch(TorAuthenticationException e) {
                log.warn("Error authenticating to Tor at " + control + ", server returned " + e.getMessage());
            } catch(SocketTimeoutException e) {
                log.warn("Timeout reading from " + control + ", is this a Tor ControlPort?");
            } catch(AccessDeniedException e) {
                log.warn("Permission denied reading Tor cookie file at " + e.getFile());
            } catch(FileSystemException e) {
                log.warn("Error reading Tor cookie file at " + e.getFile());
            } catch(Exception e) {
                log.warn("Error connecting to " + control + ", no Tor ControlPort configured?");
            }
        }
    }

    private static boolean authenticate(Socket socket) throws IOException, TorAuthenticationException {
        socket.getOutputStream().write("PROTOCOLINFO\r\n".getBytes());
        BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        String line;
        File cookieFile = null;
        while((line = reader.readLine()) != null) {
            Matcher authMatcher = TOR_AUTH_METHODS.matcher(line);
            if(authMatcher.matches()) {
                String methods = authMatcher.group(1);
                if(methods.contains("COOKIE") && !authMatcher.group(3).isEmpty()) {
                    cookieFile = new File(authMatcher.group(3));
                }
            }
            if(TOR_OK.matcher(line).matches()) {
                break;
            }
        }

        if(cookieFile != null && cookieFile.exists()) {
            byte[] cookieBytes = Files.readAllBytes(cookieFile.toPath());
            String authentication = "AUTHENTICATE " + Utils.bytesToHex(cookieBytes) + "\r\n";
            socket.getOutputStream().write(authentication.getBytes());
        } else {
            socket.getOutputStream().write("AUTHENTICATE \"\"\r\n".getBytes());
        }

        line = reader.readLine();
        if(TOR_OK.matcher(line).matches()) {
            return true;
        } else {
            throw new TorAuthenticationException(line);
        }
    }

    private static void writeNewNym(Socket socket) throws IOException {
        log.debug("Sending NEWNYM to " + socket);
        socket.getOutputStream().write("SIGNAL NEWNYM\r\n".getBytes());
    }

    private static class TorAuthenticationException extends Exception {
        public TorAuthenticationException(String message) {
            super(message);
        }
    }
}
