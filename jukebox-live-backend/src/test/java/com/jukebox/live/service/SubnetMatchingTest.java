package com.jukebox.live.service;

import com.jukebox.live.util.NetworkUtil;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class SubnetMatchingTest {

    @Test
    public void testIsSameSubnet_SameIp_ReturnsTrue() {
        assertTrue(NetworkUtil.isSameSubnet("192.168.1.100", "192.168.1.100"));
    }

    @Test
    public void testIsSameSubnet_DifferentIpsSameSubnet_ReturnsTrue() {
        assertTrue(NetworkUtil.isSameSubnet("192.168.1.100", "192.168.1.101"));
        assertTrue(NetworkUtil.isSameSubnet("192.168.1.1", "192.168.1.254"));
        assertTrue(NetworkUtil.isSameSubnet("10.0.0.50", "10.0.0.99"));
    }

    @Test
    public void testIsSameSubnet_DifferentSubnets_ReturnsFalse() {
        assertFalse(NetworkUtil.isSameSubnet("192.168.1.100", "192.168.2.100"));
        assertFalse(NetworkUtil.isSameSubnet("192.168.1.100", "10.0.0.100"));
        assertFalse(NetworkUtil.isSameSubnet("192.168.1.100", "172.16.0.100"));
    }

    @Test
    public void testIsSameSubnet_NullInputs_ReturnsFalse() {
        assertFalse(NetworkUtil.isSameSubnet(null, "192.168.1.100"));
        assertFalse(NetworkUtil.isSameSubnet("192.168.1.100", null));
        assertFalse(NetworkUtil.isSameSubnet(null, null));
    }

    @Test
    public void testIsSameSubnet_InvalidIpFormat_ReturnsFalse() {
        assertFalse(NetworkUtil.isSameSubnet("invalid", "192.168.1.100"));
        assertFalse(NetworkUtil.isSameSubnet("192.168.1", "192.168.1.100"));
        assertFalse(NetworkUtil.isSameSubnet("192.168.1.100.200", "192.168.1.100"));
        assertFalse(NetworkUtil.isSameSubnet("256.168.1.100", "192.168.1.100"));
    }

    @Test
    public void testIsSameSubnet_IPv6Addresses_ReturnsFalse() {
        assertFalse(NetworkUtil.isSameSubnet("2001:0db8:85a3:0000:0000:8a2e:0370:7334", "2001:0db8:85a3:0000:0000:8a2e:0370:7335"));
        assertFalse(NetworkUtil.isSameSubnet("::1", "192.168.1.100"));
    }

    @Test
    public void testIsSameSubnet_IPv4MappedIPv6_ReturnsFalse() {
        assertFalse(NetworkUtil.isSameSubnet("::ffff:192.168.1.100", "::ffff:192.168.1.101"));
    }

    @Test
    public void testIsValidIPv4_ValidAddresses_ReturnsTrue() {
        assertTrue(NetworkUtil.isValidIPv4("192.168.1.100"));
        assertTrue(NetworkUtil.isValidIPv4("10.0.0.1"));
        assertTrue(NetworkUtil.isValidIPv4("172.16.0.1"));
        assertTrue(NetworkUtil.isValidIPv4("0.0.0.0"));
        assertTrue(NetworkUtil.isValidIPv4("255.255.255.255"));
    }

    @Test
    public void testIsValidIPv4_InvalidAddresses_ReturnsFalse() {
        assertFalse(NetworkUtil.isValidIPv4("256.168.1.100"));
        assertFalse(NetworkUtil.isValidIPv4("192.168.1"));
        assertFalse(NetworkUtil.isValidIPv4("invalid"));
        assertFalse(NetworkUtil.isValidIPv4("192.168.1.100.200"));
        assertFalse(NetworkUtil.isValidIPv4(null));
    }

    @Test
    public void testIsIPv6_ValidAddresses_ReturnsTrue() {
        assertTrue(NetworkUtil.isIPv6("2001:0db8:85a3:0000:0000:8a2e:0370:7334"));
        assertTrue(NetworkUtil.isIPv6("::1"));
        assertTrue(NetworkUtil.isIPv6("::ffff:192.168.1.100"));
    }

    @Test
    public void testIsIPv6_InvalidAddresses_ReturnsFalse() {
        assertFalse(NetworkUtil.isIPv6("192.168.1.100"));
        assertFalse(NetworkUtil.isIPv6("invalid"));
        assertFalse(NetworkUtil.isIPv6(null));
    }
}
