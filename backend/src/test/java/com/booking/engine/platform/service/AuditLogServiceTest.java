package com.booking.engine.platform.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.booking.engine.entity.AuditAction;
import com.booking.engine.entity.AuditLog;
import com.booking.engine.entity.Organization;
import com.booking.engine.platform.repository.AuditLogRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AuditLogServiceTest {

    @Test
    void recordSavesAnEntryWithAllSuppliedFields() {
        AuditLogRepository repository = mock(AuditLogRepository.class);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        AuditLogService service = new AuditLogService(repository);

        Organization organization = new Organization();
        UUID organizationId = UUID.randomUUID();
        organization.setId(organizationId);
        UUID actorUserId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();

        service.record(organization, actorUserId, AuditAction.MEMBERSHIP_INVITED, "MEMBERSHIP", targetId,
                "invited bob@example.com as ADMIN");

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(repository).save(captor.capture());
        AuditLog saved = captor.getValue();
        assertThat(saved.getOrganization()).isSameAs(organization);
        assertThat(saved.getActorUserId()).isEqualTo(actorUserId);
        assertThat(saved.getAction()).isEqualTo(AuditAction.MEMBERSHIP_INVITED);
        assertThat(saved.getTargetType()).isEqualTo("MEMBERSHIP");
        assertThat(saved.getTargetId()).isEqualTo(targetId);
        assertThat(saved.getDetail()).isEqualTo("invited bob@example.com as ADMIN");
    }
}
