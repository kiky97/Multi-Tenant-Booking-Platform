package com.booking.engine.security;

import com.booking.engine.properties.AuthSecurityProperties;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Validates raw admin passwords before bootstrap or credential rotation.
 */
@Component
@RequiredArgsConstructor
public class AdminPasswordPolicyValidator {

    static final String GENERIC_VALIDATION_MESSAGE = "Password does not meet security requirements.";

    private final AuthSecurityProperties authSecurityProperties;

    public void validate(String password) {
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException(GENERIC_VALIDATION_MESSAGE);
        }

        AuthSecurityProperties.PasswordPolicyProperties policy = authSecurityProperties.getPasswordPolicy();
        if (password.length() < policy.getMinLength()
                || isMissingUppercase(password, policy)
                || isMissingLowercase(password, policy)
                || isMissingDigit(password, policy)
                || isMissingSpecialCharacter(password, policy)
                || isRejectedValue(password, policy.getRejectedValues())) {
            throw new IllegalArgumentException(GENERIC_VALIDATION_MESSAGE);
        }
    }

    private boolean isMissingUppercase(String password, AuthSecurityProperties.PasswordPolicyProperties policy) {
        return policy.isRequireUppercase() && password.chars().noneMatch(Character::isUpperCase);
    }

    private boolean isMissingLowercase(String password, AuthSecurityProperties.PasswordPolicyProperties policy) {
        return policy.isRequireLowercase() && password.chars().noneMatch(Character::isLowerCase);
    }

    private boolean isMissingDigit(String password, AuthSecurityProperties.PasswordPolicyProperties policy) {
        return policy.isRequireDigit() && password.chars().noneMatch(Character::isDigit);
    }

    private boolean isMissingSpecialCharacter(String password, AuthSecurityProperties.PasswordPolicyProperties policy) {
        return policy.isRequireSpecialCharacter() && password.chars().allMatch(Character::isLetterOrDigit);
    }

    private boolean isRejectedValue(String password, List<String> rejectedValues) {
        String normalizedPassword = password.trim().toLowerCase(Locale.ROOT);
        return rejectedValues != null
                && rejectedValues.stream()
                        .filter(value -> value != null && !value.isBlank())
                        .map(value -> value.trim().toLowerCase(Locale.ROOT))
                        .anyMatch(normalizedPassword::equals);
    }
}
