package com.booking.engine.platform.config;

import com.booking.engine.platform.kafka.BookingConfirmedEvent;
import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

/** Spring Boot's autoconfigured {@code KafkaTemplate} is generically {@code <Object, Object>},
 * which doesn't satisfy a {@code KafkaTemplate<String, BookingConfirmedEvent>} injection point —
 * so {@link com.booking.engine.platform.kafka.BookingEventPublisher} needs its own explicitly
 * typed template. The consumer side needs no equivalent bean: {@code @KafkaListener}'s default
 * container factory works with the generic autoconfiguration already. */
@Configuration
public class KafkaProducerConfiguration {

    @Bean
    public ProducerFactory<String, BookingConfirmedEvent> bookingEventProducerFactory(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers) {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, BookingConfirmedEvent> bookingEventKafkaTemplate(
            ProducerFactory<String, BookingConfirmedEvent> bookingEventProducerFactory) {
        return new KafkaTemplate<>(bookingEventProducerFactory);
    }
}
