package com.sdms.prototype.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sdms.prototype.service.SignatureService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
public class PublicController {
    private final SignatureService sig;
    private final ObjectMapper mapper;

    public PublicController(SignatureService sig, ObjectMapper mapper) {
        this.sig = sig;
        this.mapper = mapper;
    }

    /** Anyone holding a signed link (QR code) can check that it is genuine and unmodified. */
    @GetMapping("/api/public/verify")
    public Map<String, Object> verify(@RequestParam("p") String payload, @RequestParam("s") String signature) {
        Map<String, Object> out = new LinkedHashMap<>();
        boolean ok = sig.verify(payload, signature);
        out.put("valid", ok);
        if (!ok) {
            out.put("message", "The signature does not match. This record has been altered or was not issued by this system.");
            return out;
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> data = mapper.readValue(sig.decode(payload), Map.class);
            Object until = data.get("validUntil");
            boolean expired = until != null && Instant.now().isAfter(Instant.parse(String.valueOf(until)));
            out.put("expired", expired);
            if (expired) {
                out.put("message", "The signature is genuine, but the access window for this record has ended.");
            } else {
                out.put("payload", data);
            }
        } catch (Exception e) {
            out.put("valid", false);
            out.put("message", "The record could not be read.");
        }
        return out;
    }

    @GetMapping("/api/public/health")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }
}
