package com.globalrfq.identity.auth.service;

import com.globalrfq.identity.common.config.AppSecurityProperties;
import org.springframework.http.ResponseCookie;

import java.time.Duration;

public class RefreshCookieService {

    public static final String COOKIE_NAME = "refresh_token";
    private static final String COOKIE_PATH = "/api/auth";

    private final AppSecurityProperties props;
    public RefreshCookieService(AppSecurityProperties props) {
        this.props = props;
    }


//    helper method....
    private ResponseCookie build(String value, Duration maxAge) {
        return ResponseCookie.from(COOKIE_NAME, value)
                .httpOnly(true)
                .secure(props.cookieSecure())
                .sameSite("Strict")
                .path(COOKIE_PATH)
                .maxAge(maxAge)
                .build();
    }

//    Methods
    public ResponseCookie create(String value, long maxAgeSeconds) {
        return build(value, Duration.ofSeconds(maxAgeSeconds));
    }

    public ResponseCookie clear() {
        return build("", Duration.ZERO);
    }

}
