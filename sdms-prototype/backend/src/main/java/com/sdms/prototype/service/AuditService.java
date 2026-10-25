package com.sdms.prototype.service;

import com.sdms.prototype.domain.AuditEntry;
import com.sdms.prototype.domain.Repos.AuditRepo;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Append-only audit trail. Each entry stores a hash of itself plus the previous entry's hash. */
@Service
public class AuditService {
    private final AuditRepo repo;

    public AuditService(AuditRepo repo) {
        this.repo = repo;
    }

    public synchronized void log(String actor, String role, String action, String target,
                                 String subjectApaar, String details) {
        AuditEntry e = new AuditEntry();
        e.createdAt = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        e.actor = actor;
        e.role = role;
        e.action = action;
        e.target = target;
        e.subjectApaar = subjectApaar;
        e.details = details == null ? null : (details.length() > 950 ? details.substring(0, 950) : details);
        e.prevHash = repo.findTopByOrderByIdDesc().map(p -> p.hash).orElse("GENESIS");
        e.hash = compute(e);
        repo.save(e);
    }

    static String compute(AuditEntry e) {
        String s = String.join("|", nz(e.prevHash), String.valueOf(e.createdAt), nz(e.actor), nz(e.role),
                nz(e.action), nz(e.target), nz(e.subjectApaar), nz(e.details));
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    /** Re-computes the whole chain; reports the first broken entry, if any. */
    public Map<String, Object> verifyChain() {
        List<AuditEntry> all = repo.findAllByOrderByIdAsc();
        String prev = "GENESIS";
        Map<String, Object> out = new HashMap<>();
        out.put("entries", all.size());
        for (AuditEntry e : all) {
            if (!prev.equals(e.prevHash) || !compute(e).equals(e.hash)) {
                out.put("valid", false);
                out.put("brokenAtEntryId", e.id);
                return out;
            }
            prev = e.hash;
        }
        out.put("valid", true);
        return out;
    }
}
