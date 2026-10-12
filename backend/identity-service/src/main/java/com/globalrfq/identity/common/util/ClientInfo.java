package com.globalrfq.identity.common.util;

import jakarta.servlet.http.HttpServletRequest;

public record ClientInfo(String ipAddress, String userAgent) {

    public static ClientInfo from(HttpServletRequest request) {
        return new ClientInfo(truncate(request.getRemoteAddr(), 45),
                truncate(request.getHeader("User-Agent"), 255));
    }

    private static String truncate(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }
}