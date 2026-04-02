package com.zorvyn.finance.security;

import com.zorvyn.finance.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtUtil {

    private static final String CLAIM_TOKEN_TYPE = "type";
    private static final String CLAIM_ROLE = "role";
    private static final String TYPE_ACCESS = "ACCESS";
    private static final String TYPE_REFRESH = "REFRESH";

    private final SecretKey signingKey;
    private final long accessExpiryMs;
    private final long refreshExpiryMs;

    public JwtUtil(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.access-token-expiry-ms}") long accessExpiryMs,
            @Value("${app.jwt.refresh-token-expiry-ms}") long refreshExpiryMs) {

        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessExpiryMs = accessExpiryMs;
        this.refreshExpiryMs = refreshExpiryMs;
    }

    // ==============================
    // Token Generation
    // ==============================

    public String generateAccessToken(User user) {
        return buildToken(user, TYPE_ACCESS, accessExpiryMs);
    }

    public String generateRefreshToken(User user) {
        return buildToken(user, TYPE_REFRESH, refreshExpiryMs);
    }

    private String buildToken(User user, String tokenType, long expiryMs) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expiryMs);

        return Jwts.builder()
                .setSubject(user.getEmail()) // FIXED
                .claim(CLAIM_TOKEN_TYPE, tokenType)
                .claim(CLAIM_ROLE, user.getRole().name())
                .setIssuedAt(now) // FIXED
                .setExpiration(expiry) // FIXED
                .signWith(signingKey, SignatureAlgorithm.HS256) // FIXED
                .compact();
    }

    // ==============================
    // Token Inspection
    // ==============================

    public String extractEmail(String token) {
        return parseClaims(token).getSubject();
    }

    public boolean isTokenValid(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isRefreshToken(String token) {
        try {
            String type = parseClaims(token).get(CLAIM_TOKEN_TYPE, String.class);
            return TYPE_REFRESH.equals(type);
        } catch (Exception e) {
            return false;
        }
    }

    public long getAccessTokenExpirySeconds() {
        return accessExpiryMs / 1000;
    }

    // ==============================
    // Internal Parsing
    // ==============================

    private Claims parseClaims(String token) {
        try {
            return Jwts.parserBuilder() // FIXED
                    .setSigningKey(signingKey) // FIXED
                    .build()
                    .parseClaimsJws(token) // FIXED
                    .getBody(); // FIXED
        } catch (ExpiredJwtException e) {
            throw new RuntimeException("JWT token has expired", e);
        } catch (JwtException e) {
            throw new RuntimeException("JWT token is invalid", e);
        }
    }
}