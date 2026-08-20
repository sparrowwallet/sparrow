package com.sparrowwallet.sparrow.net;

/*
 * Copyright 2002-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import com.google.common.net.InetAddresses;
import com.sparrowwallet.sparrow.AppServices;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Locale;

/**
 * Matches a request based on IP Address or subnet mask matching against the remote
 * address.
 * <p>
 * Both IPv6 and IPv4 addresses are supported, but a matcher which is configured with an
 * IPv4 address will never match a request which returns an IPv6 address, and vice-versa.
 *
 * @author Luke Taylor
 * @since 3.0.2
 *
 * Slightly modified by omidzk to have zero dependency to any frameworks other than the JRE.
 */
public final class IpAddressMatcher {
    private static final Logger log = LoggerFactory.getLogger(IpAddressMatcher.class);

    //Names in these domains cannot resolve outside the local network - .local is resolved by link-local multicast (RFC 6762), .home.arpa is reserved for home networks (RFC 8375),
    //.internal is reserved for private use, and .lan is a suffix commonly assigned by routers
    private static final List<String> LOCAL_DOMAIN_SUFFIXES = List.of(".local", ".lan", ".home.arpa", ".internal");

    private static final List<IpAddressMatcher> LOCAL_RANGES = List.of(
            new IpAddressMatcher("10.0.0.0/8"),
            new IpAddressMatcher("172.16.0.0/12"),
            new IpAddressMatcher("192.168.0.0/16"),
            new IpAddressMatcher("100.64.0.0/10"),
            new IpAddressMatcher("127.0.0.0/8"),
            new IpAddressMatcher("169.254.0.0/16"),
            new IpAddressMatcher("::1"),
            new IpAddressMatcher("fc00::/7"),
            new IpAddressMatcher("fe80::/10"));

    private final int nMaskBits;
    private final InetAddress requiredAddress;

    /**
     * Takes a specific IP address or a range specified using the IP/Netmask (e.g.
     * 192.168.1.0/24 or 202.24.0.0/14).
     *
     * @param ipAddress the address or range of addresses from which the request must
     * come.
     */
    public IpAddressMatcher(String ipAddress) {

        if (ipAddress.indexOf('/') > 0) {
            String[] addressAndMask = ipAddress.split("/");
            ipAddress = addressAndMask[0];
            nMaskBits = Integer.parseInt(addressAndMask[1]);
        }
        else {
            nMaskBits = -1;
        }
        requiredAddress = parseAddress(ipAddress);
        assert  (requiredAddress.getAddress().length * 8 >= nMaskBits) :
                String.format("IP address %s is too short for bitmask of length %d",
                        ipAddress, nMaskBits);
    }

    public boolean matches(String address) {
        return matches(parseAddress(address));
    }

    public boolean matches(InetAddress remoteAddress) {
        if (!requiredAddress.getClass().equals(remoteAddress.getClass())) {
            return false;
        }

        if (nMaskBits < 0) {
            return remoteAddress.equals(requiredAddress);
        }

        byte[] remAddr = remoteAddress.getAddress();
        byte[] reqAddr = requiredAddress.getAddress();

        int nMaskFullBytes = nMaskBits / 8;
        byte finalByte = (byte) (0xFF00 >> (nMaskBits & 0x07));

        for (int i = 0; i < nMaskFullBytes; i++) {
            if (remAddr[i] != reqAddr[i]) {
                return false;
            }
        }

        if (finalByte != 0) {
            return (remAddr[nMaskFullBytes] & finalByte) == (reqAddr[nMaskFullBytes] & finalByte);
        }

        return true;
    }

    private static InetAddress parseAddress(String address) {
        try {
            return InetAddress.getByName(address);
        } catch(UnknownHostException e) {
            throw new IllegalArgumentException("Failed to resolve address: " + address, e);
        }
    }

    public static boolean isLocalNetworkName(String host) {
        String lowerHost = host.toLowerCase(Locale.ROOT);
        return "localhost".equals(lowerHost) || LOCAL_DOMAIN_SUFFIXES.stream().anyMatch(lowerHost::endsWith);
    }

    public static boolean isLocalNetworkAddress(String address) {
        try {
            if(isLocalNetworkName(address)) {
                return true;
            }

            //Matching a hostname against the local ranges requires resolving it, which leaks the name to (and trusts the answer of) the local DNS resolver even when a proxy is configured
            //Only IP literals are considered potentially local when using a proxy, since local network names have already returned above
            if(AppServices.isUsingProxy() && !InetAddresses.isInetAddress(address)) {
                log.info("Avoiding local DNS resolution of " + address + ", assuming it is a non-local address to be resolved by the configured proxy");
                return false;
            }

            //Resolve once for all of the ranges, as a hostname lookup may be involved
            InetAddress inetAddress = parseAddress(address);

            return LOCAL_RANGES.stream().anyMatch(localRange -> localRange.matches(inetAddress));
        } catch(IllegalArgumentException e) {
            if(AppServices.isUsingProxy()) {
                log.info(e.getMessage() + ", assuming it is a non-local address to be resolved by the configured proxy");
                return false;
            }

            throw e;
        }
    }
}
