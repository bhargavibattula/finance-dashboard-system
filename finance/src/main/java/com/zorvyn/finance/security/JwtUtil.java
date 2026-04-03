package com.zorvyn.finance.security;

import com.zorvyn.finance.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtUtil {

    private static final String CLAIM_TOKEN_TYPE = "type";
    private static final String CLAIM_ROLE        = "role";
    private static final String TYPE_ACCESS       = "ACCESS";
    private static final String TYPE_REFRESH      = "REFRESH";

    private final SecretKey signingKey;
    private final long      accessExpiryMs;
    private final long      refreshExpiryMs;

    public JwtUtil(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.access-token-expiry-ms}") long accessExpiryMs,
            @Value("${app.jwt.refresh-token-expiry-ms}") long refreshExpiryMs) {

        this.signingKey      = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessExpiryMs  = accessExpiryMs;
        this.refreshExpiryMs = refreshExpiryMs;
    }

    // ----------------------------------------------------------------
    // Token generation
    // ----------------------------------------------------------------

    public String generateAccessToken(User user) {
        return buildTokenFromUser(user, TYPE_ACCESS, accessExpiryMs);
    }

    public String generateRefreshToken(User user) {
        return buildTokenFromUser(user, TYPE_REFRESH, refreshExpiryMs);
    }

    /**
     * Issues a new access token by reading email and role from
     * an already-validated refresh token — no DB call needed.
     * Called by AuthController on POST /api/v1/auth/refresh.
     */
    public String generateAccessTokenFromRefreshToken(String refreshToken) {
        Claims claims   = parseClaims(refreshToken);
        String email    = claims.getSubject();
        String roleName = claims.get(CLAIM_ROLE, String.class);

        Date now    = new Date();
        Date expiry = new Date(now.getTime() + accessExpiryMs);

        return Jwts.builder()
                .subject(email)
                .claim(CLAIM_TOKEN_TYPE, TYPE_ACCESS)
                .claim(CLAIM_ROLE, roleName)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey)
                .compact();
    }

    // ----------------------------------------------------------------
    // Token inspection
    // ----------------------------------------------------------------

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

    // ----------------------------------------------------------------
    // Internal
    // ----------------------------------------------------------------

    private String buildTokenFromUser(User user, String tokenType, long expiryMs) {
        Date now = new Date();
        return Jwts.builder()
                .subject(user.getEmail())
                .claim(CLAIM_TOKEN_TYPE, tokenType)
                .claim(CLAIM_ROLE, user.getRole().name())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expiryMs))
                .signWith(signingKey)
                .compact();
    }

    Claims parseClaims(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            throw new RuntimeException("JWT token has expired", e);
        } catch (JwtException e) {
            throw new RuntimeException("JWT token is invalid", e);
        }
    }
}