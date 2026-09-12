package com.booking.engine.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.booking.engine.properties.AuthSecurityProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AdminPasswordPolicyValidatorTest {

    private AdminPasswordPolicyValidator validator;

    @BeforeEach
    void setUp() {
        validator = new AdminPasswordPolicyValidator(new AuthSecurityProperties());
    }

    @Test
    void validateAcceptsStrongPassword() {
        assertThatCode(() -> validator.validate("ValidAdmin123!"))
                .doesNotThrowAnyException();
    }

    @Test
    void validateRejectsTooShortPassword() {
        assertThatThrownBy(() -> validator.validate("Aa1!short"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(AdminPasswordPolicyValidator.GENERIC_VALIDATION_MESSAGE);
    }

    @Test
    void validateRejectsTriviallyWeakPassword() {
        assertThatThrownBy(() -> validator.validate("password"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(AdminPasswordPolicyValidator.GENERIC_VALIDATION_MESSAGE);
    }

    @Test
    void validateRejectsPasswordMissingRequiredCharacterClasses() {
        assertThatThrownBy(() -> validator.validate("lowercaseonly123"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(AdminPasswordPolicyValidator.GENERIC_VALIDATION_MESSAGE);
    }
}
