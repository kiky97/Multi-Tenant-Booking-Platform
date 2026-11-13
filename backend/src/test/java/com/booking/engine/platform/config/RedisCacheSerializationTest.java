package com.booking.engine.platform.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.booking.engine.platform.service.AvailabilityCatalog.AvailabilitySummary;
import com.booking.engine.platform.service.OrganizationCatalog.OrganizationSummary;
import com.booking.engine.platform.service.OrganizationServiceCatalog.ServiceSummary;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;

class RedisCacheSerializationTest {
    private final RedisCacheManager manager = new RedisCacheConfiguration().cacheManager(mock(RedisConnectionFactory.class));

    RedisCacheSerializationTest() { manager.afterPropertiesSet(); }

    @Test
    void immutableServiceListsAndRecordsRoundTrip() {
        roundTrip("organization-services", List.of(new ServiceSummary(UUID.randomUUID(), "Haircut", 30, new BigDecimal("15.50"))));
        roundTrip("organization-services", List.of());
    }

    @Test
    void availabilityTimesRoundTrip() {
        roundTrip("organization-availability", List.of(new AvailabilitySummary(UUID.randomUUID(), UUID.randomUUID(),
                DayOfWeek.MONDAY, LocalTime.of(9, 15), LocalTime.of(17, 30))));
    }

    @Test
    void organizationRecordRoundTripsAndNamespaceIsVersioned() {
        roundTrip("organizations", new OrganizationSummary(UUID.randomUUID(), "Tenant", "Europe/Zurich"));
        assertThat(manager.getCacheConfigurations().get("organizations").getKeyPrefixFor("organizations"))
                .isEqualTo("booking:cache:v2:organizations::");
    }

    @SuppressWarnings("unchecked")
    private void roundTrip(String cache, Object value) {
        var pair = (org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair<Object>)
                (Object) manager.getCacheConfigurations().get(cache).getValueSerializationPair();
        assertThat(pair.read(pair.write(value))).isEqualTo(value);
    }
}
