package com.globalrfq.identity.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.List;

public class StrongPasswordValidator implements ConstraintValidator<StrongPassword, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        List<String> problems = PasswordPolicy.violations(value);
        if (problems.isEmpty()) {
            return true;
        }
        context.disableDefaultConstraintViolation();
        problems.forEach(p -> context.buildConstraintViolationWithTemplate(p).addConstraintViolation());
        return false;
    }
}