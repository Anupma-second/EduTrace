package com.sdms.prototype.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Optional;

@Service
public class JwtService {
    private final SecretKey key;
    private final long ttlMillis;

    public JwtService(@Value("${app.jwt.secret}") String secret,
                      @Value("${app.jwt.ttl-minutes:120}") long ttlMinutes) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.ttlMillis = ttlMinutes * 60_000L;
    }

    public String issue(AuthUser u) {
        Date now = new Date();
        var b = Jwts.builder()
                .subject(u.username())
                .claim("role", u.role())
                .claim("name", u.displayName())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + ttlMillis));
        if (u.institutionId() != null) b.claim("inst", u.institutionId());
        if (u.requesterId() != null) b.claim("req", u.requesterId());
        if (u.apaarId() != null) b.claim("apaar", u.apaarId());
        return b.signWith(key).compact();
    }

    public Optional<AuthUser> parse(String token) {
        try {
            Claims c = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            Number inst = c.get("inst", Number.class);
            Number req = c.get("req", Number.class);
            return Optional.of(new AuthUser(
                    c.getSubject(),
                    c.get("role", String.class),
                    inst == null ? null : inst.longValue(),
                    req == null ? null : req.longValue(),
                    c.get("apaar", String.class),
                    c.get("name", String.class)));
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
