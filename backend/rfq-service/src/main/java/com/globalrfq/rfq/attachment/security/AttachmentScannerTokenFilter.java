package com.globalrfq.rfq.attachment.security;

import com.globalrfq.rfq.common.error.RfqErrorCode;
import com.globalrfq.rfq.common.error.SecurityErrorWriter;
import com.globalrfq.rfq.config.AttachmentScannerProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public class AttachmentScannerTokenFilter extends OncePerRequestFilter {
    public static final String PATH_PREFIX = "/internal/attachment-scanner/";
    private static final String TOKEN_HEADER = "X-Attachment-Scanner-Token";

    private final AttachmentScannerProperties properties;
    private final SecurityErrorWriter errors;

    public AttachmentScannerTokenFilter(AttachmentScannerProperties properties, SecurityErrorWriter errors) {
        this.properties = properties;
        this.errors = errors;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String expected = properties.sharedToken();
        String provided = request.getHeader(TOKEN_HEADER);
        if (expected == null || expected.length() < 32) {
            errors.write(request, response, RfqErrorCode.IDENTITY_UNAVAILABLE, "Attachment scanning is not configured");
            return;
        }
        byte[] expectedBytes = expected.getBytes(StandardCharsets.UTF_8);
        byte[] providedBytes = provided == null ? new byte[0] : provided.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expectedBytes, providedBytes)) {
            errors.write(request, response, RfqErrorCode.ACCESS_DENIED, "Attachment scanner authentication failed");
            return;
        }
        var authentication = new UsernamePasswordAuthenticationToken("attachment-scanner", null,
                java.util.List.of(new SimpleGrantedAuthority("ROLE_ATTACHMENT_SCANNER")));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        chain.doFilter(request, response);
    }
}
