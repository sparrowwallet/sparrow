package com.sparrowwallet.sparrow.net;

import com.google.common.net.HostAndPort;
import com.sparrowwallet.drongo.Utils;
import com.sparrowwallet.sparrow.AppServices;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TorUtils {
    private static final Logger log = LoggerFactory.getLogger(TorUtils.class);
    private static final Pattern TOR_OK = Pattern.compile("^2\\d{2}[ -]OK$");
    private static final Pattern TOR_AUTH_METHODS = Pattern.compile("^2\\d{2}[ -]AUTH METHODS=(\\S+)\\s?(COOKIEFILE=\"?(.+?)\"?)?$");
    private static final Pattern TOR_AUTH_CHALLENGE = Pattern.compile("^2\\d{2}[ -]AUTHCHALLENGE SERVERHASH=([0-9A-Fa-f]{64}) SERVERNONCE=([0-9A-Fa-f]{64})$");

    private static final int COOKIE_LENGTH = 32;
    private static final int CLIENT_NONCE_LENGTH = 32;
    private static final byte[] SERVER_HASH_KEY = "Tor safe cookie authentication server-to-controller hash".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] CLIENT_HASH_KEY = "Tor safe cookie authentication controller-to-server hash".getBytes(StandardCharsets.US_ASCII);

    public static void changeIdentity(HostAndPort proxy) {
        if(AppServices.isTorRunning()) {
            Tor.getDefault().changeIdentity();
        } else {
            HostAndPort control = HostAndPort.fromParts(proxy.getHost(), proxy.getPort() + 1);
            InetAddress controlAddress;
            try {
                controlAddress = InetAddress.getByName(control.getHost());
            } catch(IOException e) {
                log.warn("Cannot resolve Tor ControlPort host " + control.getHost());
                return;
            }

            //A Tor ControlPort is a loopback-only trust boundary; never open a plaintext control connection (and never read a cookie file) for a remote proxy
            if(!controlAddress.isLoopbackAddress()) {
                log.warn("Refusing to connect to non-loopback Tor ControlPort at " + control);
                return;
            }

            try(Socket socket = new Socket(controlAddress, control.getPort())) {
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
        String methods = "";
        File cookieFile = null;
        while((line = reader.readLine()) != null) {
            Matcher authMatcher = TOR_AUTH_METHODS.matcher(line);
            if(authMatcher.matches()) {
                methods = authMatcher.group(1);
                if(methods.contains("COOKIE") && authMatcher.group(3) != null && !authMatcher.group(3).isEmpty()) {
                    cookieFile = new File(authMatcher.group(3));
                }
            }
            if(TOR_OK.matcher(line).matches()) {
                break;
            }
        }

        //Only use SAFECOOKIE, which authenticates via an HMAC challenge-response and never transmits the cookie contents. The deprecated COOKIE method is deliberately not supported.
        if(methods.contains("SAFECOOKIE") && cookieFile != null) {
            authenticateSafeCookie(socket, reader, cookieFile);
        } else {
            socket.getOutputStream().write("AUTHENTICATE \"\"\r\n".getBytes());
        }

        line = reader.readLine();
        if(line != null && TOR_OK.matcher(line).matches()) {
            return true;
        } else {
            throw new TorAuthenticationException(line);
        }
    }

    private static void authenticateSafeCookie(Socket socket, BufferedReader reader, File cookieFile) throws IOException, TorAuthenticationException {
        if(!cookieFile.exists()) {
            throw new TorAuthenticationException("Cookie file " + cookieFile + " does not exist");
        }
        if(Files.size(cookieFile.toPath()) != COOKIE_LENGTH) {
            throw new TorAuthenticationException("Cookie file " + cookieFile + " is not a valid Tor cookie");
        }

        byte[] cookieBytes = Files.readAllBytes(cookieFile.toPath());
        if(cookieBytes.length != COOKIE_LENGTH) {
            throw new TorAuthenticationException("Cookie file " + cookieFile + " is not a valid Tor cookie");
        }

        byte[] clientNonce = new byte[CLIENT_NONCE_LENGTH];
        new SecureRandom().nextBytes(clientNonce);

        socket.getOutputStream().write(("AUTHCHALLENGE SAFECOOKIE " + Utils.bytesToHex(clientNonce) + "\r\n").getBytes());
        String line = reader.readLine();
        Matcher challengeMatcher = line == null ? null : TOR_AUTH_CHALLENGE.matcher(line);
        if(challengeMatcher == null || !challengeMatcher.matches()) {
            throw new TorAuthenticationException(line);
        }

        byte[] serverHash = Utils.hexToBytes(challengeMatcher.group(1));
        byte[] serverNonce = Utils.hexToBytes(challengeMatcher.group(2));
        byte[] hmacMessage = Utils.concat(Utils.concat(cookieBytes, clientNonce), serverNonce);

        //Verify the server proves it already knows the cookie before sending our own hash, so a peer that cannot read the cookie file learns nothing derived from it.
        byte[] expectedServerHash = hmacSha256(SERVER_HASH_KEY, hmacMessage);
        if(!MessageDigest.isEqual(expectedServerHash, serverHash)) {
            throw new TorAuthenticationException("Tor ControlPort failed SAFECOOKIE server hash verification");
        }

        byte[] clientHash = hmacSha256(CLIENT_HASH_KEY, hmacMessage);
        socket.getOutputStream().write(("AUTHENTICATE " + Utils.bytesToHex(clientHash) + "\r\n").getBytes());
    }

    private static byte[] hmacSha256(byte[] key, byte[] message) throws IOException {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));

            return mac.doFinal(message);
        } catch(GeneralSecurityException e) {
            throw new IOException("Could not compute SAFECOOKIE HMAC", e);
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
