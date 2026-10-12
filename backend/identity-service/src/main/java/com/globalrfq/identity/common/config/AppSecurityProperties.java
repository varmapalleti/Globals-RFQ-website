package com.globalrfq.identity.common.config;


import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security")
public record AppSecurityProperties(
        int maxFailedLoginAttempts,
        int lockDurationMinutes,
        int otpExpiryMinutes,
        int otpMaxAttempts,
        int otpMaxRequestsPerHour,
        int verificationTokenExpiryHours,
        boolean cookieSecure
){
}
