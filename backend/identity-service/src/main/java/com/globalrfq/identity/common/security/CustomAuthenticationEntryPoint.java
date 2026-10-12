package com.globalrfq.identity.common.security;

import com.globalrfq.identity.common.exception.ErrorCode;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;


import java.io.IOException;


@Component
public class CustomAuthenticationEntryPoint implements AuthenticationEntryPoint {
    public static final String ATTR_AUTH_ERROR = "auth.error.code";
    private final SecurityErrorWriter writer;

    public CustomAuthenticationEntryPoint(SecurityErrorWriter writer){
        this.writer = writer;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException ex ) throws IOException {

        Object attr = request.getAttribute(ATTR_AUTH_ERROR);
        ErrorCode code = attr instanceof ErrorCode c ? c : ErrorCode.UNAUTHORIZED;
        String message = switch(code){
            case TOKEN_EXPIRED -> "Access token has expired";
            case TOKEN_INVALID -> "Access token is invalid";
            default -> "Authentication is required";
        };
        writer.write(request, response, code, message);

    }

}
