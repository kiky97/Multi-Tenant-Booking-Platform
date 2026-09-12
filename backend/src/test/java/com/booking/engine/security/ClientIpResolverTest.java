package com.booking.engine.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.booking.engine.properties.TrustedProxyProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class ClientIpResolverTest {

    @Test
    void resolveUsesRemoteAddress() {
        ClientIpResolver resolver = resolverWithTrustedCidrs();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("198.51.100.10");

        assertThat(resolver.resolve(request)).isEqualTo("198.51.100.10");
    }

    @Test
    void resolveIgnoresSpoofedForwardedHeaders() {
        ClientIpResolver resolver = resolverWithTrustedCidrs();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("198.51.100.10");
        request.addHeader("X-Forwarded-For", "203.0.113.44");
        request.addHeader("X-Real-IP", "203.0.113.45");

        assertThat(resolver.resolve(request)).isEqualTo("198.51.100.10");
    }

    @Test
    void resolveIgnoresForwardedHeadersWhenRemoteAddressIsNotIpLiteral() {
        ClientIpResolver resolver = resolverWithTrustedCidrs("127.0.0.0/8");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("localhost");
        request.addHeader("X-Forwarded-For", "203.0.113.44");

        assertThat(resolver.resolve(request)).isEqualTo("localhost");
    }

    @Test
    void resolveUsesRightMostUntrustedForwardedAddressForTrustedProxy() {
        ClientIpResolver resolver = resolverWithTrustedCidrs("10.0.0.0/8", "192.168.0.0/16");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.5");
        request.addHeader("X-Forwarded-For", "203.0.113.44, 192.168.1.20");

        assertThat(resolver.resolve(request)).isEqualTo("203.0.113.44");
    }

    @Test
    void resolveUsesForwardedHeaderForTrustedProxy() {
        ClientIpResolver resolver = resolverWithTrustedCidrs("10.0.0.0/8");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.5");
        request.addHeader("Forwarded", "for=198.51.100.25;proto=https");

        assertThat(resolver.resolve(request)).isEqualTo("198.51.100.25");
    }

    @Test
    void resolveSkipsInvalidForwardedAddressesForTrustedProxy() {
        ClientIpResolver resolver = resolverWithTrustedCidrs("10.0.0.0/8", "192.168.0.0/16");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.5");
        request.addHeader("X-Forwarded-For", "203.0.113.44, not-an-ip, 192.168.1.20");

        assertThat(resolver.resolve(request)).isEqualTo("203.0.113.44");
    }

    @Test
    void resolveFallsBackToRemoteAddressWhenForwardedValuesAreInvalid() {
        ClientIpResolver resolver = resolverWithTrustedCidrs("10.0.0.0/8");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.5");
        request.addHeader("Forwarded", "for=not-an-ip;proto=https");
        request.addHeader("X-Forwarded-For", "also-not-an-ip");

        assertThat(resolver.resolve(request)).isEqualTo("10.0.0.5");
    }

    @Test
    void resolveReturnsUnknownWhenRemoteAddressMissing() {
        ClientIpResolver resolver = resolverWithTrustedCidrs();
        assertThat(resolver.resolve(null)).isEqualTo("unknown");
    }

    private ClientIpResolver resolverWithTrustedCidrs(String... cidrs) {
        TrustedProxyProperties properties = new TrustedProxyProperties();
        for (String cidr : cidrs) {
            properties.getTrustedProxyCidrs().add(cidr);
        }
        return new ClientIpResolver(new TrustedProxyMatcher(properties));
    }
}
