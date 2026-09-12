package com.booking.engine.service.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.booking.engine.entity.AdminRole;
import com.booking.engine.entity.AdminUserEntity;
import com.booking.engine.security.AdminPasswordPolicyValidator;
import com.booking.engine.service.AdminBootstrapPolicy;
import com.booking.engine.service.impl.AdminBootstrapPolicyImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminBootstrapPolicyTest {

    @Mock
    private AdminPasswordPolicyValidator adminPasswordPolicyValidator;

    private AdminBootstrapPolicy policy;

    @BeforeEach
    void setUp() {
        policy = new AdminBootstrapPolicyImpl(adminPasswordPolicyValidator);
    }

    @Test
    void normalizeTrimsValuesAndReturnsNullForBlankInput() {
        assertThat(policy.normalize(" admin ")).isEqualTo("admin");
        assertThat(policy.normalize("   ")).isNull();
        assertThat(policy.normalize(null)).isNull();
    }

    @Test
    void ensureBootstrapAccountStatePromotesRoleAndActiveFlag() {
        AdminUserEntity adminUser = AdminUserEntity.builder()
                .username("admin")
                .role(null)
                .active(false)
                .build();

        assertThat(policy.ensureBootstrapAccountState(adminUser)).isTrue();
        assertThat(adminUser.getRole()).isEqualTo(AdminRole.ADMIN);
        assertThat(adminUser.getActive()).isTrue();
    }

    @Test
    void validateBootstrapPasswordWrapsPolicyFailures() {
        org.mockito.Mockito.doThrow(new IllegalArgumentException("weak"))
                .when(adminPasswordPolicyValidator)
                .validate("weak");

        assertThatThrownBy(() -> policy.validateBootstrapPassword("weak"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Admin bootstrap password does not meet security requirements.")
                .hasCauseInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void incrementTokenVersionUsesNonNegativeBase() {
        AdminUserEntity adminUser = AdminUserEntity.builder()
                .username("admin")
                .tokenVersion(-5)
                .build();

        policy.incrementTokenVersion(adminUser);

        assertThat(adminUser.getTokenVersion()).isEqualTo(1);
    }

    @Test
    void resolveBootstrapReasonCodeReturnsExpectedValue() {
        assertThat(policy.resolveBootstrapReasonCode(true, false)).isEqualTo("CREATED");
        assertThat(policy.resolveBootstrapReasonCode(false, true)).isEqualTo("PASSWORD_OVERWRITE");
        assertThat(policy.resolveBootstrapReasonCode(false, false)).isEqualTo("UPDATED");
    }
}
