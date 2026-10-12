package com.globalrfq.identity.common.security;

import com.globalrfq.identity.common.config.GatewayProperties;
import com.globalrfq.identity.common.exception.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.sql.rowset.serial.SerialException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public class GatewaySecretFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(GatewaySecretFilter.class);

    private final GatewayProperties props;
    private final SecurityErrorWriter writer;

    public GatewaySecretFilter(GatewayProperties props, SecurityErrorWriter writer){
        this.props = props;
        this.writer = writer;
        if(props.secretEnabled() && (props.sharedSecret() == null || props.sharedSecret().length() < 32)){
            throw new IllegalStateException("GATEWAY_SHARED_SECRET must be at least 32 characters when the gateway secret check is enabled");
        }
    }


    @Override
    protected boolean shouldNotFilter(HttpServletRequest request){
        return !props.secretEnabled() || request.getRequestURI().startsWith("/actuator/health");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        String provided = request.getHeader(props.headerName());
        byte[] expectedBytes = props.sharedSecret().getBytes(StandardCharsets.UTF_8);
        byte[] providedBytes = provided == null ? new byte[0] : provided.getBytes(StandardCharsets.UTF_8);

        if (!MessageDigest.isEqual(expectedBytes, providedBytes)) {
            log.warn("Blocked request without a valid gateway secret: {} {}", request.getMethod(), request.getRequestURI());
            writer.write(request, response, ErrorCode.ACCESS_DENIED, "Requests must come through the API Gateway");
            return;
        }
        chain.doFilter(request, response);

    }

}
