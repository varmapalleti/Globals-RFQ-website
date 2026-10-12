package com.globalrfq.identity.common.security;


import com.globalrfq.identity.common.exception.ErrorCode;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import org.springframework.http.HttpHeaders;
import java.util.List;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String BEARER = "Bearer ";
    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService){
        this.jwtService = jwtService;
    }


    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);

        if(header != null && header.startsWith(BEARER) && SecurityContextHolder.getContext().getAuthentication() == null){
            String token = header.substring(BEARER.length()).trim();
            try{
                AuthenticatedUser user = jwtService.toAuthenticatedUser(jwtService.parse(token));


                List<SimpleGrantedAuthority> authorities = user.roles().stream().map(r -> new SimpleGrantedAuthority("ROLE_" + r)).toList();

                var auth = new UsernamePasswordAuthenticationToken(user, null, authorities);

                auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContextHolder.getContext().setAuthentication(auth);
            }catch(ExpiredJwtException ex){
                request.setAttribute(CustomAuthenticationEntryPoint.ATTR_AUTH_ERROR, ErrorCode.TOKEN_EXPIRED);
            }catch(JwtException | IllegalArgumentException ex){
                log.warn("Rejected invalid JWT on {}: {}", request.getRequestURI(), ex.getClass().getSimpleName());
                request.setAttribute(CustomAuthenticationEntryPoint.ATTR_AUTH_ERROR, ErrorCode.TOKEN_INVALID);
            }
        }
        chain.doFilter(request, response);
    }
}
