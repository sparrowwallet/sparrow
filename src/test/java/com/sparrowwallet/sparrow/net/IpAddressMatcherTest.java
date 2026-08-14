package com.sparrowwallet.sparrow.net;

import com.sparrowwallet.drongo.Network;
import com.sparrowwallet.sparrow.SparrowWallet;
import com.sparrowwallet.sparrow.io.Config;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class IpAddressMatcherTest {
    @TempDir
    private static Path tempHome;

    @BeforeAll
    public static void setUp() {
        //Isolate Config.get() from the developer's config so the proxy setting can be changed safely
        System.setProperty(SparrowWallet.APP_HOME_PROPERTY, tempHome.toString());
        Network.set(Network.MAINNET);
    }

    @AfterAll
    public static void tearDown() {
        Config.get().setUseProxy(false);
        System.clearProperty(SparrowWallet.APP_HOME_PROPERTY);
    }

    @Test
    public void classifiesAddressesWithoutProxy() {
        Config.get().setUseProxy(false);
        assertTrue(IpAddressMatcher.isLocalNetworkAddress("localhost"));
        assertTrue(IpAddressMatcher.isLocalNetworkAddress("127.0.0.1"));
        assertTrue(IpAddressMatcher.isLocalNetworkAddress("192.168.1.5"));
        assertTrue(IpAddressMatcher.isLocalNetworkAddress("10.0.0.10"));
        assertTrue(IpAddressMatcher.isLocalNetworkAddress("172.16.0.1"));
        assertTrue(IpAddressMatcher.isLocalNetworkAddress("100.64.0.1"));
        assertFalse(IpAddressMatcher.isLocalNetworkAddress("8.8.8.8"));
    }

    @Test
    public void classifiesIpLiteralsWithProxy() {
        Config.get().setUseProxy(true);
        //IP literals classify without DNS resolution, so local network servers must still connect directly when a proxy is configured
        assertTrue(IpAddressMatcher.isLocalNetworkAddress("localhost"));
        assertTrue(IpAddressMatcher.isLocalNetworkAddress("127.0.0.1"));
        assertTrue(IpAddressMatcher.isLocalNetworkAddress("192.168.1.5"));
        assertTrue(IpAddressMatcher.isLocalNetworkAddress("10.0.0.10"));
        assertTrue(IpAddressMatcher.isLocalNetworkAddress("172.16.0.1"));
        assertTrue(IpAddressMatcher.isLocalNetworkAddress("100.64.0.1"));
        assertFalse(IpAddressMatcher.isLocalNetworkAddress("8.8.8.8"));
    }

    @Test
    public void assumesHostnamesAreRemoteWithProxy() {
        Config.get().setUseProxy(true);
        //Hostnames are not resolved by the local DNS resolver when a proxy is configured, and are assumed to be remote addresses resolved by the proxy
        assertFalse(IpAddressMatcher.isLocalNetworkAddress("electrumx.example.com"));
        assertFalse(IpAddressMatcher.isLocalNetworkAddress("notarealhost.invalid"));
    }
}
