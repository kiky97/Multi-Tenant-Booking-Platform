package com.booking.engine.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.booking.engine.properties.TrustedProxyProperties;
import org.junit.jupiter.api.Test;

class TrustedProxyMatcherTest {

    @Test
    void isTrustedMatchesExactIpAndCidrRanges() {
        TrustedProxyProperties properties = new TrustedProxyProperties();
        properties.getTrustedProxyCidrs().add("10.0.0.5");
        properties.getTrustedProxyCidrs().add("192.168.1.0/24");
        TrustedProxyMatcher matcher = new TrustedProxyMatcher(properties);

        assertThat(matcher.isTrusted("10.0.0.5")).isTrue();
        assertThat(matcher.isTrusted("192.168.1.42")).isTrue();
        assertThat(matcher.isTrusted("192.168.2.42")).isFalse();
        assertThat(matcher.isTrusted("203.0.113.8")).isFalse();
    }

    @Test
    void isTrustedMatchesIpv6ExactIpAndCidrRanges() {
        TrustedProxyProperties properties = new TrustedProxyProperties();
        properties.getTrustedProxyCidrs().add("2001:db8::1");
        properties.getTrustedProxyCidrs().add("fd00:1234::/32");
        TrustedProxyMatcher matcher = new TrustedProxyMatcher(properties);

        assertThat(matcher.isTrusted("2001:db8::1")).isTrue();
        assertThat(matcher.isTrusted("fd00:1234::abcd")).isTrue();
        assertThat(matcher.isTrusted("fd00:5678::abcd")).isFalse();
    }

    @Test
    void isTrustedDoesNotResolveHostnames() {
        TrustedProxyProperties properties = new TrustedProxyProperties();
        properties.getTrustedProxyCidrs().add("127.0.0.0/8");
        TrustedProxyMatcher matcher = new TrustedProxyMatcher(properties);

        assertThat(matcher.isTrusted("localhost")).isFalse();
        assertThat(matcher.isIpLiteral("localhost")).isFalse();
        assertThat(matcher.isIpLiteral("127.0.0.1")).isTrue();
    }

    @Test
    void constructorRejectsTrustedProxyHostnames() {
        TrustedProxyProperties properties = new TrustedProxyProperties();
        properties.getTrustedProxyCidrs().add("localhost");

        assertThatThrownBy(() -> new TrustedProxyMatcher(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Invalid trusted proxy address");
    }
}
