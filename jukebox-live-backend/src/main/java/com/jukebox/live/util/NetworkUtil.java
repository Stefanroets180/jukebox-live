package com.jukebox.live.util;

import java.util.regex.Pattern;

public class NetworkUtil {

    private static final Pattern IPV4_PATTERN = Pattern.compile(
        "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$"
    );

    private static final Pattern IPV6_PATTERN = Pattern.compile(
        "^([0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}$|^::1$|^::$"
    );

    private static final Pattern IPV4_MAPPED_IPV6_PATTERN = Pattern.compile(
        "^::ffff:(\\d{1,3}\\.){3}\\d{1,3}$"
    );

    /**
     * Checks if two IPv4 addresses are on the same /24 subnet.
     * This is a reasonable assumption for typical home Wi-Fi networks.
     * 
     * @param ip1 First IPv4 address
     * @param ip2 Second IPv4 address
     * @return true if both addresses are valid IPv4 and on the same /24 subnet, false otherwise
     */
    public static boolean isSameSubnet(String ip1, String ip2) {
        if (ip1 == null || ip2 == null) return false;
        if (ip1.equals(ip2)) return true;
        
        // Reject IPv6 addresses (not supported for subnet matching in this implementation)
        if (isIPv6(ip1) || isIPv6(ip2)) {
            return false;
        }

        // Handle IPv4-mapped IPv6 addresses (::ffff:x.x.x.x)
        ip1 = extractIPv4FromMapped(ip1);
        ip2 = extractIPv4FromMapped(ip2);
        
        // Validate IPv4 format
        if (!isValidIPv4(ip1) || !isValidIPv4(ip2)) {
            return false;
        }

        // Parse and compare first 3 octets (assumes /24 subnet)
        String[] octets1 = ip1.split("\\.");
        String[] octets2 = ip2.split("\\.");
        
        return octets1[0].equals(octets2[0]) && 
               octets1[1].equals(octets2[1]) && 
               octets1[2].equals(octets2[2]);
    }

    /**
     * Validates if a string is a valid IPv4 address.
     */
    public static boolean isValidIPv4(String ip) {
        return ip != null && IPV4_PATTERN.matcher(ip).matches();
    }

    /**
     * Checks if a string is an IPv6 address (including IPv4-mapped).
     */
    public static boolean isIPv6(String ip) {
        if (ip == null) return false;
        return IPV6_PATTERN.matcher(ip).matches() || IPV4_MAPPED_IPV6_PATTERN.matcher(ip).matches();
    }

    /**
     * Extracts IPv4 address from IPv4-mapped IPv6 format (::ffff:x.x.x.x).
     * Returns the original string if not in mapped format.
     */
    private static String extractIPv4FromMapped(String ip) {
        if (ip != null && IPV4_MAPPED_IPV6_PATTERN.matcher(ip).matches()) {
            return ip.substring(7); // Remove "::ffff:" prefix
        }
        return ip;
    }
}
