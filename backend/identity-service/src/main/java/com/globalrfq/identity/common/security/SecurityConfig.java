package com.globalrfq.identity.common.security;


import com.globalrfq.identity.common.config.GatewayProperties;
import com.globalrfq.identity.common.config.CorsProperties;
import com.globalrfq.identity.common.config.InternalServiceProperties;
import com.globalrfq.identity.user.repository.UserRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private static final String[] PUBLIC_POST = {
            "/api/auth/login",
            "/api/auth/refresh",
            "/api/auth/verify-email",
            "/api/auth/accept-invite",
            "/api/auth/resend-verification",
            "/api/auth/forgot-password",
            "/api/auth/verify-reset-otp",
            "/api/auth/reset-password"
    };


    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtService jwtService,
            UserRepository userRepository,
            GatewayProperties gatewayProperties,
            InternalServiceProperties internalServiceProperties,
            CorsProperties corsProperties,
            SecurityErrorWriter errorWriter,
            CustomAuthenticationEntryPoint entryPoint,
            CustomAccessDeniedHandler accessDeniedHandler
    ) throws Exception{
        JwtAuthenticationFilter jwtFilter = new JwtAuthenticationFilter(jwtService, userRepository);
        GatewaySecretFilter gatewaySecretFilter = new GatewaySecretFilter(gatewayProperties, errorWriter);
        InternalServiceTokenFilter internalServiceTokenFilter = new InternalServiceTokenFilter(internalServiceProperties, errorWriter);
        RefreshOriginFilter refreshOriginFilter = new RefreshOriginFilter(corsProperties, errorWriter);

        http
                .cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(e -> e.authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(a -> a.requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/internal/identity/**").hasRole("RFQ_SERVICE")
                        .requestMatchers(HttpMethod.POST, PUBLIC_POST).permitAll()
                        .requestMatchers("/api/test/public/**").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(internalServiceTokenFilter, JwtAuthenticationFilter.class)
                .addFilterBefore(refreshOriginFilter, JwtAuthenticationFilter.class)
                .addFilterBefore(gatewaySecretFilter, JwtAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

}
