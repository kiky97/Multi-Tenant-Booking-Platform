package com.booking.engine.config;

import com.booking.engine.properties.HairSalonProperties;
import com.booking.engine.repository.HairSalonRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Verifies that the configured singleton salon exists in the migrated database.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
@RequiredArgsConstructor
public class HairSalonConfigurationValidator implements ApplicationRunner {

    private final HairSalonProperties hairSalonProperties;
    private final HairSalonRepository hairSalonRepository;

    @Override
    @Transactional(readOnly = true)
    public void run(ApplicationArguments args) {
        UUID hairSalonId = hairSalonProperties.getId();
        if (hairSalonId == null) {
            throw new IllegalStateException("app.hair-salon.id must be configured.");
        }

        if (!hairSalonRepository.existsById(hairSalonId)) {
            throw new IllegalStateException(
                    "Configured app.hair-salon.id=" + hairSalonId
                            + " does not exist in hair_salon table. Use the seeded singleton salon id "
                            + "or create a matching hair_salon row before startup.");
        }
    }
}
