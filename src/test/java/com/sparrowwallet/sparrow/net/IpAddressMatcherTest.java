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
    public void classifiesLoopbackAndIpv6AddressesWithoutProxy() {
        Config.get().setUseProxy(false);
        assertTrue(IpAddressMatcher.isLocalNetworkAddress("127.0.0.2"));
        assertTrue(IpAddressMatcher.isLocalNetworkAddress("169.254.1.1"));
        assertTrue(IpAddressMatcher.isLocalNetworkAddress("::1"));
        assertTrue(IpAddressMatcher.isLocalNetworkAddress("fd00::1"));
        assertTrue(IpAddressMatcher.isLocalNetworkAddress("fc00::1"));
        assertTrue(IpAddressMatcher.isLocalNetworkAddress("fe80::1"));
        //Global unicast addresses remain remote, whichever family they are in
        assertFalse(IpAddressMatcher.isLocalNetworkAddress("2001:4860:4860::8888"));
        assertFalse(IpAddressMatcher.isLocalNetworkAddress("8.8.8.8"));
    }

    @Test
    public void classifiesLoopbackAndIpv6AddressesWithProxy() {
        Config.get().setUseProxy(true);
        //IP literals classify without DNS resolution, so a local node must still connect directly when a proxy is configured
        assertTrue(IpAddressMatcher.isLocalNetworkAddress("127.0.0.2"));
        assertTrue(IpAddressMatcher.isLocalNetworkAddress("::1"));
        assertTrue(IpAddressMatcher.isLocalNetworkAddress("fd00::1"));
        assertTrue(IpAddressMatcher.isLocalNetworkAddress("fe80::1"));
        assertFalse(IpAddressMatcher.isLocalNetworkAddress("2001:4860:4860::8888"));
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
    public void classifiesLocalNetworkNamesWithoutProxy() {
        Config.get().setUseProxy(false);
        //Names in these domains cannot resolve outside the local network, and are classified without resolving them
        assertTrue(IpAddressMatcher.isLocalNetworkAddress("mynode.local"));
        assertTrue(IpAddressMatcher.isLocalNetworkAddress("mynode.lan"));
        assertTrue(IpAddressMatcher.isLocalNetworkAddress("MyNode.Home.Arpa"));
        assertTrue(IpAddressMatcher.isLocalNetworkAddress("mynode.internal"));
    }

    @Test
    public void classifiesLocalNetworkNamesWithProxy() {
        Config.get().setUseProxy(true);
        //A local network name must still connect directly when a proxy is configured, as the proxy cannot resolve it
        assertTrue(IpAddressMatcher.isLocalNetworkAddress("mynode.local"));
        assertTrue(IpAddressMatcher.isLocalNetworkAddress("mynode.lan"));
        assertTrue(IpAddressMatcher.isLocalNetworkAddress("mynode.internal"));
        //A local domain suffix elsewhere in the name is not a local network name
        assertFalse(IpAddressMatcher.isLocalNetworkAddress("mynode.local.example.com"));
    }

    @Test
    public void assumesHostnamesAreRemoteWithProxy() {
        Config.get().setUseProxy(true);
        //Hostnames are not resolved by the local DNS resolver when a proxy is configured, and are assumed to be remote addresses resolved by the proxy
        assertFalse(IpAddressMatcher.isLocalNetworkAddress("electrumx.example.com"));
        assertFalse(IpAddressMatcher.isLocalNetworkAddress("notarealhost.invalid"));
    }
}
