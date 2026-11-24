package com.globalrfq.rfq.common.auth;

import com.globalrfq.rfq.common.error.SecurityErrorWriter;
import com.globalrfq.rfq.attachment.security.AttachmentScannerTokenFilter;
import com.globalrfq.rfq.config.AttachmentScannerProperties;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.io.IOException;

@Configuration
@EnableMethodSecurity
public class RfqSecurityConfig {
    @Bean
    SecurityFilterChain rfqSecurityFilterChain(HttpSecurity http,
                                              IdentityAuthorizationClient identityClient,
                                              GatewayProperties gatewayProperties,
                                              AttachmentScannerProperties scannerProperties,
                                              SecurityErrorWriter errorWriter) throws Exception {
        RfqGatewaySecretFilter gatewayFilter = new RfqGatewaySecretFilter(gatewayProperties, errorWriter);
        IdentityBearerAuthenticationFilter identityFilter = new IdentityBearerAuthenticationFilter(identityClient, errorWriter);
        AttachmentScannerTokenFilter scannerFilter = new AttachmentScannerTokenFilter(scannerProperties, errorWriter);

        http.csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) -> errorWriter.write(
                                request, response, com.globalrfq.rfq.common.error.RfqErrorCode.UNAUTHORIZED, "Authentication is required"))
                        .accessDeniedHandler((request, response, exception) -> errorWriter.write(
                                request, response, com.globalrfq.rfq.common.error.RfqErrorCode.ACCESS_DENIED,
                                "You do not have permission to perform this action")))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/internal/attachment-scanner/**").hasRole("ATTACHMENT_SCANNER")
                        .anyRequest().authenticated())
                .addFilterBefore(identityFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(gatewayFilter, IdentityBearerAuthenticationFilter.class)
                .addFilterBefore(scannerFilter, RfqGatewaySecretFilter.class);
        return http.build();
    }
}
