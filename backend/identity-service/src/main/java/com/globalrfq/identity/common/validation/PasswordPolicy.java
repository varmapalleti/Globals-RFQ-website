package com.globalrfq.identity.common.validation;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class PasswordPolicy {

    public static final int MIN_LENGTH = 8;
    public static final int MAX_LENGTH = 64;
    private static final int BCRYPT_MAX_BYTES = 72;

    private PasswordPolicy() {}

    public static List<String> violations(String password) {
        List<String> problems = new ArrayList<>();
        if (password == null) {
            return problems;
        }
        if (password.length() < MIN_LENGTH) {
            problems.add("Password must be at least " + MIN_LENGTH + " characters long");
        }
        if (password.length() > MAX_LENGTH
                || password.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_BYTES) {
            problems.add("Password must be at most " + MAX_LENGTH + " characters long");
        }
        if (password.chars().noneMatch(Character::isUpperCase)) {
            problems.add("Password must contain at least one uppercase letter");
        }
        if (password.chars().noneMatch(Character::isLowerCase)) {
            problems.add("Password must contain at least one lowercase letter");
        }
        if (password.chars().noneMatch(Character::isDigit)) {
            problems.add("Password must contain at least one number");
        }
        if (password.chars().allMatch(c -> Character.isLetterOrDigit(c) || Character.isWhitespace(c))) {
            problems.add("Password must contain at least one special character");
        }
        if (password.chars().anyMatch(Character::isWhitespace)) {
            problems.add("Password must not contain spaces");
        }
        return problems;
    }
}