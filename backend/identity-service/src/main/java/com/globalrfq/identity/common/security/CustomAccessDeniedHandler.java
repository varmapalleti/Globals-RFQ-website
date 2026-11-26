package com.globalrfq.identity.common.security;


import com.globalrfq.identity.common.exception.ErrorCode;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class CustomAccessDeniedHandler implements AccessDeniedHandler {

    private final SecurityErrorWriter writer;

    public CustomAccessDeniedHandler(SecurityErrorWriter writer){
        this.writer = writer;
    }


    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException) throws IOException, ServletException {
        writer.write(request, response, ErrorCode.ACCESS_DENIED,"You do not have permission to perform this action");
    }
}
