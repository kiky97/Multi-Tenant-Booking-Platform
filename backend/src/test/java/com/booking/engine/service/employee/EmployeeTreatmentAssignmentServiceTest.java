package com.booking.engine.service.employee;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.booking.engine.entity.TreatmentEntity;
import com.booking.engine.repository.TreatmentRepository;
import com.booking.engine.service.EmployeeTreatmentAssignmentService;
import com.booking.engine.service.impl.EmployeeTreatmentAssignmentServiceImpl;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EmployeeTreatmentAssignmentServiceTest {

    @Mock
    private TreatmentRepository treatmentRepository;

    private EmployeeTreatmentAssignmentService service;

    @BeforeEach
    void setUp() {
        service = new EmployeeTreatmentAssignmentServiceImpl(treatmentRepository);
    }

    @Test
    void resolveRequestedTreatmentsReturnsEmptySetWhenNoIdsProvided() {
        assertThat(service.resolveRequestedTreatments(null)).isEmpty();
        verifyNoInteractions(treatmentRepository);
    }

    @Test
    void resolveRequestedTreatmentsNormalizesIdsAndReturnsActiveTreatments() {
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();

        TreatmentEntity first = new TreatmentEntity();
        first.setId(firstId);
        first.setName("Cut");
        first.setActive(true);

        TreatmentEntity second = new TreatmentEntity();
        second.setId(secondId);
        second.setName("Color");
        second.setActive(true);

        when(treatmentRepository.findAllByIdInAndActiveTrue(anySet()))
                .thenReturn(List.of(first, second));

        assertThat(service.resolveRequestedTreatments(java.util.Arrays.asList(firstId, null, firstId, secondId)))
                .containsExactly(first, second);
    }

    @Test
    void resolveRequestedTreatmentsRejectsUnknownOrInactiveIds() {
        UUID treatmentId = UUID.randomUUID();

        when(treatmentRepository.findAllByIdInAndActiveTrue(anySet()))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.resolveRequestedTreatments(List.of(treatmentId)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("selected services");
    }
}
