package com.sdms.prototype.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/** One-time passwords kept in memory (prototype). Production would use a shared store and rate limiting. */
@Service
public class OtpService {
    private record Entry(String code, Instant expires, int attempts) {}

    private final ConcurrentHashMap<String, Entry> store = new ConcurrentHashMap<>();
    private final SecureRandom rnd = new SecureRandom();

    /** If set (app.otp.fixed-code), every one-time code is this value. Leave empty for random codes. */
    @Value("${app.otp.fixed-code:}")
    private String fixedCode;

    public String issue(String key) {
        String code = (fixedCode != null && fixedCode.matches("\\d{6}")) ? fixedCode : String.format("%06d", rnd.nextInt(1_000_000));
        store.put(key, new Entry(code, Instant.now().plusSeconds(600), 0));
        return code;
    }

    public boolean verify(String key, String code) {
        Entry e = store.get(key);
        if (e == null || code == null) return false;
        if (Instant.now().isAfter(e.expires()) || e.attempts() >= 5) {
            store.remove(key);
            return false;
        }
        if (e.code().equals(code.trim())) {
            store.remove(key);
            return true;
        }
        store.put(key, new Entry(e.code(), e.expires(), e.attempts() + 1));
        return false;
    }
}
