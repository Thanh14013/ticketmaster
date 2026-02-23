package com.ticketmaster.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Utility class cho JWT operations: generate, validate, extract claims.
 *
 * <p>Dùng JJWT 0.12.x với HS256 (HMAC-SHA256).
 * Secret key phải ít nhất 256 bits (32 bytes) – set qua {@code jwt.secret} trong application.yml.
 *
 * <p>Được sử dụng bởi:
 * <ul>
 *   <li>{@code user-service}    – generate access/refresh tokens khi login</li>
 *   <li>{@code api-gateway}     – validate token trên mọi request đến</li>
 *   <li>{@code booking-service} – extract userId từ token để gắn với booking</li>
 * </ul>
 *
 * <p>Token payload (claims):
 * <pre>
 * {
 *   "sub":   "user-uuid",       ← userId
 *   "email": "user@example.com",
 *   "role":  "ROLE_USER",
 *   "iat":   1700000000,
 *   "exp":   1700086400
 * }
 * </pre>
 */
@Slf4j
@Component
public class JwtUtils {

    private final SecretKey secretKey;
    private final long      accessTokenExpirationMs;
    private final long      refreshTokenExpirationMs;

    public JwtUtils(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration-ms:86400000}") long accessTokenExpirationMs,
            @Value("${jwt.refresh-expiration-ms:604800000}") long refreshTokenExpirationMs) {

        this.secretKey               = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpirationMs  = accessTokenExpirationMs;
        this.refreshTokenExpirationMs = refreshTokenExpirationMs;
    }

    // ── Token Generation ─────────────────────────────────────────

    /**
     * Tạo access token từ userId, email, role.
     *
     * @param userId ID của user (subject)
     * @param email  email của user
     * @param role   role của user (vd: "ROLE_USER", "ROLE_ADMIN")
     * @return JWT access token string
     */
    public String generateAccessToken(String userId, String email, String role) {
        Map<String, Object> extraClaims = new HashMap<>();
        extraClaims.put("email", email);
        extraClaims.put("role",  role);
        return buildToken(extraClaims, userId, accessTokenExpirationMs);
    }

    /**
     * Tạo refresh token (chỉ có sub, không có extra claims).
     *
     * @param userId ID của user
     * @return JWT refresh token string
     */
    public String generateRefreshToken(String userId) {
        return buildToken(new HashMap<>(), userId, refreshTokenExpirationMs);
    }

    private String buildToken(Map<String, Object> extraClaims, String subject,
                               long expirationMs) {
        Date now       = new Date();
        Date expiresAt = new Date(now.getTime() + expirationMs);

        return Jwts.builder()
                .claims(extraClaims)
                .subject(subject)
                .issuedAt(now)
                .expiration(expiresAt)
                .signWith(secretKey)
                .compact();
    }

    // ── Token Validation ─────────────────────────────────────────

    /**
     * Validate token: chữ ký đúng, chưa hết hạn, format hợp lệ.
     *
     * @param token JWT token string
     * @return {@code true} nếu token hợp lệ
     */
    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (SignatureException e) {
            log.warn("JWT signature invalid: {}", e.getMessage());
        } catch (MalformedJwtException e) {
            log.warn("JWT malformed: {}", e.getMessage());
        } catch (ExpiredJwtException e) {
            log.warn("JWT expired: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            log.warn("JWT unsupported: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("JWT claims empty: {}", e.getMessage());
        }
        return false;
    }

    // ── Claims Extraction ────────────────────────────────────────

    /**
     * Lấy userId (subject) từ token.
     *
     * @param token JWT token string
     * @return userId (UUID string)
     */
    public String extractUserId(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    /**
     * Lấy email từ token claims.
     */
    public String extractEmail(String token) {
        return extractClaim(token, claims -> claims.get("email", String.class));
    }

    /**
     * Lấy role từ token claims.
     */
    public String extractRole(String token) {
        return extractClaim(token, claims -> claims.get("role", String.class));
    }

    /**
     * Kiểm tra token đã hết hạn chưa.
     */
    public boolean isTokenExpired(String token) {
        try {
            Date expiration = extractClaim(token, Claims::getExpiration);
            return expiration.before(new Date());
        } catch (ExpiredJwtException e) {
            return true;
        }
    }

    /**
     * Generic method để extract bất kỳ claim nào từ token.
     *
     * @param token          JWT token string
     * @param claimsResolver function để lấy claim từ {@link Claims}
     * @param <T>            kiểu của claim value
     * @return giá trị claim
     */
    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        Claims claims = parseClaims(token);
        return claimsResolver.apply(claims);
    }

    // ── Private Helpers ──────────────────────────────────────────

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}