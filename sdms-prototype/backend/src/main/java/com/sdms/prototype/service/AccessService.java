package com.sdms.prototype.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sdms.prototype.domain.*;
import com.sdms.prototype.domain.Repos.*;
import com.sdms.prototype.security.AuthUser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Student-Controlled Access: nobody sees a student's data unless the student approves a specific request.
 * Two trust tiers (verified organisation / general individual) decide which fields can ever be shared.
 */
@Service
public class AccessService {
    public static final int DECIDE_HOURS = 48;
    public static final String GENERIC_MESSAGE =
            "If this APAAR ID is registered, the request now appears in the student's profile under 'Access requests'. The status will appear in 'My requests' once they respond.";

    private final AccessRepo access;
    private final RequesterRepo requesters;
    private final BlockRepo blocks;
    private final ApaarRepo registry;
    private final RecordRepo records;
    private final InstitutionRepo institutions;
    private final CourseRepo courses;
    private final MailService mail;
    private final AuditService audit;
    private final SignatureService sig;
    private final ObjectMapper mapper;
    private final String frontendUrl;

    public AccessService(AccessRepo access, RequesterRepo requesters, BlockRepo blocks, ApaarRepo registry,
                         RecordRepo records, InstitutionRepo institutions, CourseRepo courses, MailService mail,
                         AuditService audit, SignatureService sig, ObjectMapper mapper,
                         @Value("${app.frontend-url}") String frontendUrl) {
        this.access = access;
        this.requesters = requesters;
        this.blocks = blocks;
        this.registry = registry;
        this.records = records;
        this.institutions = institutions;
        this.courses = courses;
        this.mail = mail;
        this.audit = audit;
        this.sig = sig;
        this.mapper = mapper;
        this.frontendUrl = frontendUrl;
    }

    public static List<String> allowedFields(String tier) {
        return Const.TIER_VERIFIED.equals(tier) ? Const.FIELDS_VERIFIED : Const.FIELDS_GENERAL;
    }

    public static int maxDays(String tier) {
        return Const.TIER_VERIFIED.equals(tier) ? 30 : 1;
    }

    static Set<String> toSet(String csv) {
        if (csv == null || csv.isBlank()) return new LinkedHashSet<>();
        return Arrays.stream(csv.split(",")).map(String::trim).filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    // ------------------------------------------------------------------ requester side

    @Transactional
    public String createRequest(AuthUser me, String apaarId, String purpose, List<String> fields, int days) {
        Requester req = requesters.findById(me.requesterId()).orElseThrow();
        if (apaarId == null || !apaarId.trim().matches("\\d{12}")) throw new IllegalArgumentException("APAAR ID must be exactly 12 digits.");
        apaarId = apaarId.trim();
        if (purpose == null || purpose.isBlank()) throw new IllegalArgumentException("State the purpose of the request.");
        if (purpose.length() > 400) throw new IllegalArgumentException("Purpose is too long (max 400 characters).");
        if (fields == null || fields.isEmpty()) throw new IllegalArgumentException("Select at least one field.");
        List<String> allowed = allowedFields(req.tier);
        for (String f : fields) {
            if (!allowed.contains(f)) throw new IllegalArgumentException("The field '" + f + "' is not available at your trust level.");
        }
        int d = Math.max(1, Math.min(days, maxDays(req.tier)));
        long recent = access.countByRequesterIdAndCreatedAtAfter(req.id, Instant.now().minus(24, ChronoUnit.HOURS));
        long limit = Const.TIER_VERIFIED.equals(req.tier) ? 50 : 5;
        if (recent >= limit) throw new IllegalArgumentException("Daily request limit reached for your trust level (" + limit + ").");

        ApaarPerson p = registry.findById(apaarId).orElse(null);
        if (p == null) {
            audit.log(me.username(), Const.ROLE_REQUESTER, "ACCESS_REQUEST_UNKNOWN_ID", "requester#" + req.id, null, "no matching APAAR ID");
            return GENERIC_MESSAGE;
        }
        AccessRequest a = new AccessRequest();
        a.requesterId = req.id;
        a.apaarId = apaarId;
        a.purpose = purpose.trim();
        a.fields = String.join(",", new LinkedHashSet<>(fields));
        a.durationDays = d;
        a.createdAt = Instant.now();
        a.decideBy = a.createdAt.plus(DECIDE_HOURS, ChronoUnit.HOURS);
        if (blocks.existsByApaarIdAndRequesterId(apaarId, req.id)) {
            a.status = Const.REQ_DENIED;
            a.decidedAt = a.createdAt;
            access.save(a);
            audit.log(me.username(), Const.ROLE_REQUESTER, "ACCESS_REQUEST_BLOCKED", "request#" + a.id, apaarId, "auto-denied (blocked by student)");
            return GENERIC_MESSAGE;
        }
        a.status = Const.REQ_PENDING;
        access.save(a);
        audit.log(me.username(), Const.ROLE_REQUESTER, "ACCESS_REQUEST", "request#" + a.id, apaarId,
                "tier=" + req.tier + " fields=" + a.fields + " days=" + d);
        String who = req.fullName + (req.organisation == null || req.organisation.isBlank() ? "" : " (" + req.organisation + ")");
        String trust = Const.TIER_VERIFIED.equals(req.tier) ? "VERIFIED ORGANISATION" : "UNVERIFIED INDIVIDUAL - identity checked by email only";
        mail.send(p.email, "Someone has asked to view your enrolment details",
                "Dear " + p.name + ",\n\n" + who + " has asked to view information linked to your APAAR ID.\n\n"
                        + "Trust level : " + trust + "\n"
                        + "Purpose     : " + a.purpose + "\n"
                        + "Fields      : " + a.fields + "\n"
                        + "Duration    : up to " + d + " day(s) if you approve\n\n"
                        + "Nothing is shared unless you approve. Sign in to approve, change what is shared, or deny:\n"
                        + frontendUrl + "/student/requests/" + a.id + "\n\n"
                        + "This request expires in " + DECIDE_HOURS + " hours. If you do not recognise the requester, deny it and block them.",
                "ACCESS_REQUEST");
        return GENERIC_MESSAGE;
    }

    public List<Map<String, Object>> listForRequester(Long requesterId) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (AccessRequest a : access.findByRequesterIdOrderByIdDesc(requesterId)) {
            refresh(a);
            Map<String, Object> m = baseView(a);
            m.put("apaarId", a.apaarId);
            out.add(m);
        }
        return out;
    }

    @Transactional
    public Map<String, Object> data(AuthUser me, Long id) {
        AccessRequest a = access.findById(id).orElseThrow(() -> new NoSuchElementException("Request not found"));
        if (!a.requesterId.equals(me.requesterId())) throw new NoSuchElementException("Request not found");
        refresh(a);
        if (!Const.REQ_APPROVED.equals(a.status)) throw new IllegalArgumentException("Access is not available: request is " + a.status + ".");
        Requester req = requesters.findById(a.requesterId).orElseThrow();
        Set<String> f = toSet(a.approvedFields);
        List<EnrolmentRecord> recs = records.findByApaarId(a.apaarId).stream()
                .filter(r -> !"WITHDRAWN".equals(r.status)).collect(Collectors.toList());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("requestId", a.id);
        payload.put("apaarId", a.apaarId);
        payload.put("issuedAt", Instant.now().truncatedTo(ChronoUnit.SECONDS).toString());
        payload.put("validUntil", a.expiresAt.toString());
        payload.put("tier", req.tier);
        if (f.contains("enrolled")) payload.put("enrolled", recs.stream().anyMatch(r -> "ACTIVE".equals(r.status)));
        if (f.contains("name") && !recs.isEmpty()) payload.put("name", recs.get(0).name);
        boolean wantRows = f.stream().anyMatch(x -> List.of("institution", "course", "admissionYear", "status", "confirmation").contains(x));
        if (wantRows) {
            List<Map<String, Object>> rows = new ArrayList<>();
            for (EnrolmentRecord r : recs) {
                Map<String, Object> row = new LinkedHashMap<>();
                if (f.contains("institution")) row.put("institution", institutions.findById(r.institutionId).map(i -> i.name).orElse(""));
                if (f.contains("course")) row.put("course", courses.findById(r.courseId).map(c -> c.name).orElse(""));
                if (f.contains("admissionYear")) row.put("admissionYear", r.admissionYear);
                if (f.contains("status")) row.put("status", r.status);
                if (f.contains("confirmation")) row.put("studentConfirmation", r.confirmationStatus);
                rows.add(row);
            }
            payload.put("records", rows);
        }
        a.viewCount++;
        access.save(a);
        audit.log(me.username(), Const.ROLE_REQUESTER, "ACCESS_VIEW", "request#" + a.id, a.apaarId, "fields=" + a.approvedFields);
        return signed(payload);
    }

    public Map<String, Object> signed(Map<String, Object> payload) {
        try {
            String json = mapper.writeValueAsString(payload);
            String b64 = sig.encode(json);
            String s = sig.sign(b64);
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("payload", payload);
            out.put("payloadEncoded", b64);
            out.put("signature", s);
            out.put("verifyPath", "/verify?p=" + b64 + "&s=" + s);
            return out;
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    // ------------------------------------------------------------------ student side

    public List<Map<String, Object>> listForStudent(String apaarId) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (AccessRequest a : access.findByApaarIdOrderByIdDesc(apaarId)) {
            refresh(a);
            Requester r = requesters.findById(a.requesterId).orElse(null);
            Map<String, Object> m = baseView(a);
            m.put("requesterId", a.requesterId);
            m.put("requesterName", r == null ? "" : r.fullName);
            m.put("organisation", r == null ? "" : r.organisation);
            m.put("tier", r == null ? Const.TIER_GENERAL : r.tier);
            m.put("blocked", blocks.existsByApaarIdAndRequesterId(apaarId, a.requesterId));
            m.put("allowedFields", allowedFields(r == null ? Const.TIER_GENERAL : r.tier));
            out.add(m);
        }
        return out;
    }

    @Transactional
    public Map<String, Object> decide(String apaarId, Long id, boolean approve, List<String> chosen, Integer days) {
        AccessRequest a = owned(apaarId, id);
        refresh(a);
        if (!Const.REQ_PENDING.equals(a.status)) throw new IllegalArgumentException("This request is already " + a.status + ".");
        Requester req = requesters.findById(a.requesterId).orElseThrow();
        a.decidedAt = Instant.now();
        if (!approve) {
            a.status = Const.REQ_DENIED;
        } else {
            Set<String> requested = toSet(a.fields);
            Set<String> pick = chosen == null ? new LinkedHashSet<>(requested) : new LinkedHashSet<>(chosen);
            pick.retainAll(requested);
            pick.retainAll(allowedFields(req.tier));
            if (pick.isEmpty()) throw new IllegalArgumentException("Select at least one field to share, or deny the request.");
            int d = days == null ? a.durationDays : days;
            d = Math.max(1, Math.min(d, Math.min(a.durationDays, maxDays(req.tier))));
            a.approvedFields = String.join(",", pick);
            a.expiresAt = a.decidedAt.plus(d, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
            a.status = Const.REQ_APPROVED;
        }
        access.save(a);
        audit.log(apaarId, Const.ROLE_STUDENT, approve ? "ACCESS_APPROVED" : "ACCESS_DENIED", "request#" + a.id, apaarId,
                approve ? "fields=" + a.approvedFields + " until=" + a.expiresAt : "denied");
        notifyRequester(req, a, approve);
        return baseView(a);
    }

    @Transactional
    public Map<String, Object> revoke(String apaarId, Long id) {
        AccessRequest a = owned(apaarId, id);
        refresh(a);
        if (!Const.REQ_APPROVED.equals(a.status)) throw new IllegalArgumentException("Only an active approval can be revoked.");
        a.status = Const.REQ_REVOKED;
        a.decidedAt = Instant.now();
        access.save(a);
        audit.log(apaarId, Const.ROLE_STUDENT, "ACCESS_REVOKED", "request#" + a.id, apaarId, "revoked by student");
        return baseView(a);
    }

    @Transactional
    public void block(String apaarId, Long requesterId) {
        if (!blocks.existsByApaarIdAndRequesterId(apaarId, requesterId)) {
            BlockedRequester b = new BlockedRequester();
            b.apaarId = apaarId;
            b.requesterId = requesterId;
            blocks.save(b);
        }
        for (AccessRequest a : access.findByApaarIdOrderByIdDesc(apaarId)) {
            if (a.requesterId.equals(requesterId) && (Const.REQ_PENDING.equals(a.status) || Const.REQ_APPROVED.equals(a.status))) {
                a.status = Const.REQ_DENIED;
                a.decidedAt = Instant.now();
                access.save(a);
            }
        }
        audit.log(apaarId, Const.ROLE_STUDENT, "REQUESTER_BLOCKED", "requester#" + requesterId, apaarId, "blocked by student");
    }

    @Transactional
    public void unblock(String apaarId, Long blockId) {
        BlockedRequester b = blocks.findById(blockId).orElseThrow(() -> new NoSuchElementException("Not found"));
        if (!apaarId.equals(b.apaarId)) throw new NoSuchElementException("Not found");
        blocks.delete(b);
        audit.log(apaarId, Const.ROLE_STUDENT, "REQUESTER_UNBLOCKED", "requester#" + b.requesterId, apaarId, "unblocked");
    }

    public List<Map<String, Object>> blockedList(String apaarId) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (BlockedRequester b : blocks.findByApaarId(apaarId)) {
            Requester r = requesters.findById(b.requesterId).orElse(null);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", b.id);
            m.put("requesterId", b.requesterId);
            m.put("requesterName", r == null ? "" : r.fullName);
            m.put("organisation", r == null ? "" : r.organisation);
            out.add(m);
        }
        return out;
    }

    // ------------------------------------------------------------------ helpers

    private AccessRequest owned(String apaarId, Long id) {
        AccessRequest a = access.findById(id).orElseThrow(() -> new NoSuchElementException("Request not found"));
        if (!apaarId.equals(a.apaarId)) throw new NoSuchElementException("Request not found");
        return a;
    }

    /** Applies time-based expiry on read. */
    void refresh(AccessRequest a) {
        Instant now = Instant.now();
        boolean changed = false;
        if (Const.REQ_PENDING.equals(a.status) && a.decideBy != null && now.isAfter(a.decideBy)) {
            a.status = Const.REQ_EXPIRED;
            changed = true;
        } else if (Const.REQ_APPROVED.equals(a.status) && a.expiresAt != null && now.isAfter(a.expiresAt)) {
            a.status = Const.REQ_EXPIRED;
            changed = true;
        }
        if (changed) access.save(a);
    }

    public int expireAll() {
        int n = 0;
        for (String s : List.of(Const.REQ_PENDING, Const.REQ_APPROVED)) {
            for (AccessRequest a : access.findByStatus(s)) {
                String before = a.status;
                refresh(a);
                if (!before.equals(a.status)) n++;
            }
        }
        return n;
    }

    private Map<String, Object> baseView(AccessRequest a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", a.id);
        m.put("purpose", a.purpose);
        m.put("requestedFields", toSet(a.fields));
        m.put("approvedFields", toSet(a.approvedFields));
        m.put("durationDays", a.durationDays);
        m.put("status", a.status);
        m.put("createdAt", a.createdAt);
        m.put("decideBy", a.decideBy);
        m.put("decidedAt", a.decidedAt);
        m.put("expiresAt", a.expiresAt);
        m.put("viewCount", a.viewCount);
        return m;
    }

    private void notifyRequester(Requester req, AccessRequest a, boolean approved) {
        mail.send(req.email, approved ? "Your access request was approved" : "Your access request was declined",
                approved
                        ? "The student approved request #" + a.id + " for: " + a.approvedFields + ". Access ends " + a.expiresAt
                        + ". Sign in and open 'My requests' to view it."
                        : "The student declined request #" + a.id + ". No data is available for this request.",
                "ACCESS_DECISION");
    }
}
