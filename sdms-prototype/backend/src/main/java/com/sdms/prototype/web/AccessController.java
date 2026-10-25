package com.sdms.prototype.web;

import com.sdms.prototype.domain.Repos.RequesterRepo;
import com.sdms.prototype.domain.Requester;
import com.sdms.prototype.security.AuthUser;
import com.sdms.prototype.service.AccessService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/** Requester side of Student-Controlled Access (employers, regulators, individuals). */
@RestController
@RequestMapping("/api/access")
public class AccessController {
    private final AccessService access;
    private final RequesterRepo requesters;

    public AccessController(AccessService access, RequesterRepo requesters) {
        this.access = access;
        this.requesters = requesters;
    }

    @GetMapping("/me")
    public Map<String, Object> me(@AuthenticationPrincipal AuthUser me) {
        Requester r = requesters.findById(me.requesterId()).orElseThrow();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.id);
        m.put("fullName", r.fullName);
        m.put("organisation", r.organisation);
        m.put("tier", r.tier);
        m.put("allowedFields", AccessService.allowedFields(r.tier));
        m.put("maxDays", AccessService.maxDays(r.tier));
        return m;
    }

    @PostMapping("/requests")
    @SuppressWarnings("unchecked")
    public Map<String, String> create(@AuthenticationPrincipal AuthUser me, @RequestBody Map<String, Object> body) {
        List<String> fields = body.get("fields") instanceof List<?> l ? (List<String>) l : List.of();
        int days = body.get("durationDays") instanceof Number n ? n.intValue() : 1;
        String msg = access.createRequest(me, String.valueOf(body.getOrDefault("apaarId", "")),
                String.valueOf(body.getOrDefault("purpose", "")), fields, days);
        return Map.of("message", msg);
    }

    @GetMapping("/requests")
    public List<Map<String, Object>> list(@AuthenticationPrincipal AuthUser me) {
        return access.listForRequester(me.requesterId());
    }

    @GetMapping("/requests/{id}/data")
    public Map<String, Object> data(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        return access.data(me, id);
    }
}
