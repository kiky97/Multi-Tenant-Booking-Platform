package com.booking.engine.platform.config;

import com.booking.engine.platform.kafka.BookingConfirmedEvent;
import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;

/** Spring Boot 4.0.7 has no Kafka autoconfiguration module (confirmed: no "kafka" class anywhere
 * in spring-boot-autoconfigure-4.0.7.jar, and no spring-boot-kafka module in the BOM — unlike
 * Redis/JPA/persistence, which each got their own boot-* module in the 4.x split). Without
 * {@code @EnableKafka} and these two beans, {@code @KafkaListener} methods are never wired to a
 * container: no error, no log line, they just never run. One shared container factory is enough
 * — each {@code @KafkaListener}'s own {@code groupId} is what gives
 * {@link com.booking.engine.platform.kafka.NotificationConsumer} and
 * {@link com.booking.engine.platform.kafka.BookingAnalyticsConsumer} independent consumer groups. */
@Configuration
@EnableKafka
public class KafkaConsumerConfiguration {

    @Bean
    public ConsumerFactory<String, BookingConfirmedEvent> bookingEventConsumerFactory(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers) {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "com.booking.engine.platform.kafka");
        return new DefaultKafkaConsumerFactory<>(props);
    }

    /** Named to match the default {@code containerFactory} Spring looks for when a
     * {@code @KafkaListener} doesn't specify one explicitly. */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, BookingConfirmedEvent> kafkaListenerContainerFactory(
            ConsumerFactory<String, BookingConfirmedEvent> bookingEventConsumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, BookingConfirmedEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(bookingEventConsumerFactory);
        return factory;
    }
}
