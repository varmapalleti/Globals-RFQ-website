package com.globalrfq.identity.common.security;

import com.globalrfq.identity.common.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;


@Service
public class JwtService {

    private static final String CLAIM_EMAIL = "email";
    private static final String CLAIM_ROLES = "roles";
    private static final String CLAIM_TYPE = "token_type";
    private static final String TYPE_ACCESS = "access";

    private final JwtProperties props;
    private SecretKey signingKey;

    public JwtService(JwtProperties props){
        this.props = props;
    }

    @PostConstruct
    void init(){
        byte[] keyBytes = Decoders.BASE64.decode(props.secret());
        if(keyBytes.length <32){
            throw new IllegalStateException("JWT_SECRET must decode to at least 32 bytes (256 bits).");
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
    }


    public String generateAccessToken(Long userId, String email, Collection<String> roles){
        Instant now = Instant.now();
        Instant expiry = now.plus(Duration.ofMinutes(props.accessTokenExpiryMinutes()));
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .issuer(props.issuer())
                .subject(String.valueOf(userId))
                .claim(CLAIM_EMAIL, email)
                .claim(CLAIM_ROLES, List.copyOf(roles))
                .claim(CLAIM_TYPE, TYPE_ACCESS)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(signingKey)
                .compact();
    }

    public Claims parse(String token){
        return Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(props.issuer())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    @SuppressWarnings("unchecked")
    public AuthenticatedUser toAuthenticatedUser(Claims claims){
        if(!TYPE_ACCESS.equals(claims.get(CLAIM_TYPE, String.class))){
            throw new io.jsonwebtoken.JwtException("Not an access token");
        }
        Long id = Long.valueOf(claims.getSubject());
        String email = claims.get(CLAIM_EMAIL, String.class);
        List <String> roles = claims.get(CLAIM_ROLES, List.class);
        Set<String> roleSet = roles == null ? Set.of() : roles.stream().map(String:: valueOf).collect(Collectors.toUnmodifiableSet());
        return new AuthenticatedUser(id, email, roleSet);
    }

    public long accessTokenTtlSeconds(){
        return Duration.ofMinutes(props.accessTokenExpiryMinutes()).toSeconds();
    }

}
