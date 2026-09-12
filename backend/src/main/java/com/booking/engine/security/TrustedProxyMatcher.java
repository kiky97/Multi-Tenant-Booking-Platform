package com.booking.engine.security;

import com.booking.engine.properties.TrustedProxyProperties;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Matches remote addresses against the configured trusted proxy allowlist.
 */
@Component
public class TrustedProxyMatcher {

    private final List<AddressRule> rules;

    public TrustedProxyMatcher(TrustedProxyProperties properties) {
        this.rules = properties.getTrustedProxyCidrs().stream()
                .map(this::normalize)
                .filter(value -> value != null)
                .map(this::parseRule)
                .toList();
    }

    public boolean isTrusted(String remoteAddress) {
        byte[] address = parseAddress(remoteAddress);
        if (address == null) {
            return false;
        }

        for (AddressRule rule : rules) {
            if (rule.matches(address)) {
                return true;
            }
        }
        return false;
    }

    public boolean isIpLiteral(String value) {
        return parseAddress(value) != null;
    }

    private AddressRule parseRule(String value) {
        String normalized = normalize(value);

        int cidrSeparator = normalized.indexOf('/');
        if (cidrSeparator < 0) {
            byte[] address = requireAddress(normalized);
            return new AddressRule(address, address.length * Byte.SIZE);
        }

        String addressPart = normalized.substring(0, cidrSeparator).trim();
        String prefixPart = normalized.substring(cidrSeparator + 1).trim();
        byte[] address = requireAddress(addressPart);
        int prefixLength;
        try {
            prefixLength = Integer.parseInt(prefixPart);
        } catch (NumberFormatException exception) {
            throw new IllegalStateException("Invalid trusted proxy CIDR prefix: " + normalized, exception);
        }

        int maxPrefixLength = address.length * Byte.SIZE;
        if (prefixLength < 0 || prefixLength > maxPrefixLength) {
            throw new IllegalStateException("Trusted proxy CIDR prefix is out of range: " + normalized);
        }

        return new AddressRule(address, prefixLength);
    }

    private byte[] requireAddress(String value) {
        byte[] address = parseAddress(value);
        if (address == null) {
            throw new IllegalStateException("Invalid trusted proxy address: " + value);
        }
        return address;
    }

    private byte[] parseAddress(String value) {
        String normalized = normalize(value);
        if (normalized == null) {
            return null;
        }

        if (normalized.indexOf(':') >= 0) {
            return parseIpv6Address(normalized);
        }
        return parseIpv4Address(normalized);
    }

    private byte[] parseIpv4Address(String value) {
        String[] parts = value.split("\\.", -1);
        if (parts.length != 4) {
            return null;
        }

        byte[] bytes = new byte[4];
        for (int index = 0; index < parts.length; index++) {
            String part = parts[index];
            if (part.isBlank() || part.length() > 3 || !part.chars().allMatch(Character::isDigit)) {
                return null;
            }

            int octet = Integer.parseInt(part);
            if (octet > 255) {
                return null;
            }
            bytes[index] = (byte) octet;
        }
        return bytes;
    }

    private byte[] parseIpv6Address(String value) {
        if (value.indexOf(":::") >= 0
                || (value.startsWith(":") && !value.startsWith("::"))
                || (value.endsWith(":") && !value.endsWith("::"))) {
            return null;
        }

        int compressionIndex = value.indexOf("::");
        if (compressionIndex != value.lastIndexOf("::")) {
            return null;
        }

        boolean compressed = compressionIndex >= 0;
        String left = compressed ? value.substring(0, compressionIndex) : value;
        String right = compressed ? value.substring(compressionIndex + 2) : "";

        List<Integer> leftHextets = parseIpv6Hextets(left, !compressed);
        List<Integer> rightHextets = parseIpv6Hextets(right, true);
        if (leftHextets == null || rightHextets == null) {
            return null;
        }

        int totalHextets = leftHextets.size() + rightHextets.size();
        if (compressed) {
            if (totalHextets >= 8) {
                return null;
            }
        } else if (totalHextets != 8) {
            return null;
        }

        List<Integer> hextets = new ArrayList<>(8);
        hextets.addAll(leftHextets);
        for (int index = totalHextets; index < 8; index++) {
            hextets.add(0);
        }
        hextets.addAll(rightHextets);

        byte[] bytes = new byte[16];
        for (int index = 0; index < hextets.size(); index++) {
            int hextet = hextets.get(index);
            bytes[index * 2] = (byte) (hextet >> Byte.SIZE);
            bytes[index * 2 + 1] = (byte) hextet;
        }
        return bytes;
    }

    private List<Integer> parseIpv6Hextets(String value, boolean allowIpv4Suffix) {
        List<Integer> hextets = new ArrayList<>();
        if (value.isEmpty()) {
            return hextets;
        }

        String[] parts = value.split(":", -1);
        for (int index = 0; index < parts.length; index++) {
            String part = parts[index];
            if (part.isEmpty()) {
                return null;
            }

            if (part.indexOf('.') >= 0) {
                if (!allowIpv4Suffix || index != parts.length - 1) {
                    return null;
                }

                byte[] ipv4Bytes = parseIpv4Address(part);
                if (ipv4Bytes == null) {
                    return null;
                }
                hextets.add(((ipv4Bytes[0] & 0xFF) << Byte.SIZE) | (ipv4Bytes[1] & 0xFF));
                hextets.add(((ipv4Bytes[2] & 0xFF) << Byte.SIZE) | (ipv4Bytes[3] & 0xFF));
                continue;
            }

            if (part.length() > 4 || !part.chars().allMatch(this::isHexDigit)) {
                return null;
            }
            hextets.add(Integer.parseInt(part, 16));
        }
        return hextets;
    }

    private boolean isHexDigit(int character) {
        return (character >= '0' && character <= '9')
                || (character >= 'a' && character <= 'f')
                || (character >= 'A' && character <= 'F');
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim();
        return normalized.isBlank() ? null : normalized;
    }

    private static final class AddressRule {

        private final byte[] network;
        private final int prefixLength;

        private AddressRule(byte[] network, int prefixLength) {
            this.network = network.clone();
            this.prefixLength = prefixLength;
        }

        private boolean matches(byte[] candidate) {
            if (candidate.length != network.length) {
                return false;
            }

            int wholeBytes = prefixLength / Byte.SIZE;
            int remainingBits = prefixLength % Byte.SIZE;

            for (int index = 0; index < wholeBytes; index++) {
                if (candidate[index] != network[index]) {
                    return false;
                }
            }

            if (remainingBits == 0 || wholeBytes >= candidate.length) {
                return true;
            }

            int mask = 0xFF << (Byte.SIZE - remainingBits);
            return (candidate[wholeBytes] & mask) == (network[wholeBytes] & mask);
        }
    }
}
