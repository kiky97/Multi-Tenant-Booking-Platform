package com.booking.engine.platform.config;

import com.booking.engine.platform.service.AvailabilityCatalog.AvailabilitySummary;
import com.booking.engine.platform.service.OrganizationCatalog.OrganizationSummary;
import com.booking.engine.platform.service.OrganizationServiceCatalog.ServiceSummary;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

/** Explicit cache value types support immutable lists/records without polymorphic type metadata. */
@Configuration
public class RedisCacheConfiguration {
    @Bean
    RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        var types = mapper.getTypeFactory();
        var defaults = org.springframework.data.redis.cache.RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10));
        return RedisCacheManager.builder(connectionFactory).cacheDefaults(defaults)
                .withInitialCacheConfigurations(Map.of(
                        "organizations", typed(defaults, mapper, types.constructType(OrganizationSummary.class)),
                        "organization-services", typed(defaults, mapper, types.constructCollectionType(List.class, ServiceSummary.class)),
                        "organization-availability", typed(defaults, mapper, types.constructCollectionType(List.class, AvailabilitySummary.class))))
                // New payload shape gets a new namespace; old cache values expire without breaking rolling upgrades.
                .build();
    }

    private static org.springframework.data.redis.cache.RedisCacheConfiguration typed(
            org.springframework.data.redis.cache.RedisCacheConfiguration defaults, ObjectMapper mapper, JavaType type) {
        return defaults.computePrefixWith(name -> "booking:cache:v2:" + name + "::")
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new Jackson2JsonRedisSerializer<>(mapper, type)));
    }
}
