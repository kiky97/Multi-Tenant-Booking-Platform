package com.booking.engine.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.booking.engine.properties.HairSalonProperties;
import com.booking.engine.repository.HairSalonRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;

@ExtendWith(MockitoExtension.class)
class HairSalonConfigurationValidatorTest {

    private static final UUID SEEDED_HAIR_SALON_ID =
            UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

    @Mock
    private HairSalonProperties hairSalonProperties;

    @Mock
    private HairSalonRepository hairSalonRepository;

    @InjectMocks
    private HairSalonConfigurationValidator validator;

    @Test
    void runAllowsConfiguredSalonWhenItExists() {
        when(hairSalonProperties.getId()).thenReturn(SEEDED_HAIR_SALON_ID);
        when(hairSalonRepository.existsById(SEEDED_HAIR_SALON_ID)).thenReturn(true);

        assertThatCode(() -> validator.run(arguments()))
                .doesNotThrowAnyException();

        verify(hairSalonRepository).existsById(SEEDED_HAIR_SALON_ID);
    }

    @Test
    void runRejectsConfiguredSalonWhenItDoesNotExist() {
        UUID missingHairSalonId = UUID.fromString("560e8400-e29b-41d4-a716-546655440000");
        when(hairSalonProperties.getId()).thenReturn(missingHairSalonId);
        when(hairSalonRepository.existsById(missingHairSalonId)).thenReturn(false);

        assertThatThrownBy(() -> validator.run(arguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Configured app.hair-salon.id=" + missingHairSalonId
                        + " does not exist in hair_salon table. Use the seeded singleton salon id "
                        + "or create a matching hair_salon row before startup.");
    }

    @Test
    void runRejectsMissingConfiguredSalonId() {
        when(hairSalonProperties.getId()).thenReturn(null);

        assertThatThrownBy(() -> validator.run(arguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("app.hair-salon.id must be configured.");

        verifyNoInteractions(hairSalonRepository);
    }

    private DefaultApplicationArguments arguments() {
        return new DefaultApplicationArguments(new String[0]);
    }
}
