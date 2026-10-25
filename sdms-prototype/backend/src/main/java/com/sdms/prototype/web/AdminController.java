package com.sdms.prototype.web;

import com.sdms.prototype.domain.*;
import com.sdms.prototype.domain.Repos.*;
import com.sdms.prototype.security.AuthUser;
import com.sdms.prototype.service.*;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/** AICTE administrator functions. */
@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final MonitoringService monitoring;
    private final CaseService cases;
    private final AlertRepo alerts;
    private final PolicyService policy;
    private final AuditService audit;
    private final AuditRepo auditRepo;
    private final RequesterRepo requesters;
    private final RecordRepo records;
    private final CourseRepo courses;
    private final InstitutionRepo institutions;
    private final HistoryRepo history;

    public AdminController(MonitoringService monitoring, CaseService cases, AlertRepo alerts, PolicyService policy,
                           AuditService audit, AuditRepo auditRepo, RequesterRepo requesters,
                           RecordRepo records, CourseRepo courses, InstitutionRepo institutions, HistoryRepo history) {
        this.monitoring = monitoring;
        this.cases = cases;
        this.alerts = alerts;
        this.policy = policy;
        this.audit = audit;
        this.auditRepo = auditRepo;
        this.requesters = requesters;
        this.records = records;
        this.courses = courses;
        this.institutions = institutions;
        this.history = history;
    }

    private static String s(Map<String, Object> m, String k) {
        Object v = m.get(k);
        return v == null ? "" : String.valueOf(v).trim();
    }

    @GetMapping("/overview")
    public Map<String, Object> overview() {
        return monitoring.overview();
    }


    /** Student-wise records across all institutions (read only). */
    @GetMapping("/students")
    public Map<String, Object> students(@AuthenticationPrincipal AuthUser me,
                                        @RequestParam(required = false) String q,
                                        @RequestParam(required = false) Long institutionId,
                                        @RequestParam(required = false) Integer year,
                                        @RequestParam(required = false) String issue,
                                        @RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "25") int size) {
        Map<Long, Course> crs = courses.findAll().stream().collect(Collectors.toMap(c -> c.id, c -> c));
        Map<Long, Institution> inst = institutions.findAll().stream().collect(Collectors.toMap(i -> i.id, i -> i));
        String needle = q == null ? "" : q.trim().toLowerCase();
        List<EnrolmentRecord> all = records.findAll().stream()
                .filter(r -> institutionId == null || institutionId.equals(r.institutionId))
                .filter(r -> year == null || r.admissionYear == year)
                .filter(r -> issue == null || issue.isEmpty() || ("APAAR".equals(issue) && !Const.APAAR_VALID.equals(r.apaarStatus))
                        || ("DISPUTED".equals(issue) && Const.CONF_DISPUTED.equals(r.confirmationStatus))
                        || ("DUPLICATE".equals(issue) && r.flags != null && r.flags.contains("DUPLICATE_ENROLMENT")))
                .filter(r -> needle.isEmpty() || (r.name != null && r.name.toLowerCase().contains(needle))
                        || (r.apaarId != null && r.apaarId.contains(needle))
                        || (r.rollNo != null && r.rollNo.toLowerCase().contains(needle)))
                .sorted(Comparator.comparing((EnrolmentRecord r) -> r.id).reversed())
                .collect(Collectors.toList());
        int sz = Math.max(1, Math.min(size, 100));
        int from = Math.min(Math.max(page, 0) * sz, all.size());
        int to = Math.min(from + sz, all.size());
        List<Map<String, Object>> rows = new ArrayList<>();
        for (EnrolmentRecord r : all.subList(from, to)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.id);
            m.put("apaarId", r.apaarId);
            m.put("rollNo", r.rollNo);
            m.put("name", r.name);
            m.put("dob", r.dob);
            m.put("gender", r.gender);
            m.put("institution", inst.containsKey(r.institutionId) ? inst.get(r.institutionId).name : "");
            m.put("course", crs.containsKey(r.courseId) ? crs.get(r.courseId).name : "");
            m.put("admissionYear", r.admissionYear);
            m.put("category", r.category);
            m.put("seatType", r.seatType);
            m.put("residency", r.residency);
            m.put("status", r.status);
            m.put("apaarStatus", r.apaarStatus);
            m.put("confirmationStatus", r.confirmationStatus);
            m.put("confirmationNote", r.confirmationNote);
            m.put("flags", r.flags);
            rows.add(m);
        }
        if (!needle.isEmpty()) {
            audit.log(me.username(), Const.ROLE_ADMIN, "ADMIN_STUDENT_SEARCH", "students", null, "search by administrator");
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("total", all.size());
        out.put("page", page);
        out.put("size", sz);
        out.put("rows", rows);
        return out;
    }

    @GetMapping("/students/{id}/history")
    public List<RecordHistory> studentHistory(@PathVariable Long id) {
        return history.findByRecordIdOrderByIdDesc(id);
    }

    @GetMapping("/institutions")
    public List<Map<String, Object>> institutions() {
        return monitoring.institutionTable();
    }

    @GetMapping("/alerts")
    public List<Map<String, Object>> alerts() {
        return monitoring.alertViews(null);
    }

    @PostMapping("/alerts/{id}/status")
    public Map<String, String> alertStatus(@AuthenticationPrincipal AuthUser me, @PathVariable Long id,
                                           @RequestBody Map<String, Object> body) {
        String status = s(body, "status");
        if (!List.of("OPEN", "REVIEWED", "CLOSED").contains(status)) throw new IllegalArgumentException("Invalid status.");
        Alert a = alerts.findById(id).orElseThrow(() -> new NoSuchElementException("Alert not found"));
        a.status = status;
        a.note = s(body, "note");
        a.updatedAt = Instant.now();
        alerts.save(a);
        audit.log(me.username(), Const.ROLE_ADMIN, "ALERT_STATUS", "alert#" + id, null, status + (a.note.isEmpty() ? "" : " | " + a.note));
        return Map.of("message", "Alert updated.");
    }

    @PostMapping("/alerts/recompute")
    public Map<String, Object> recompute(@AuthenticationPrincipal AuthUser me) {
        int n = monitoring.recomputeAlerts();
        audit.log(me.username(), Const.ROLE_ADMIN, "ALERTS_RECOMPUTED", "alerts", null, "active conditions=" + n);
        return Map.of("activeConditions", n);
    }

    @GetMapping("/duplicates")
    public List<Map<String, Object>> duplicates() {
        return cases.duplicateViews(null);
    }

    @PostMapping("/duplicates/{id}/resolve")
    public Map<String, String> resolve(@AuthenticationPrincipal AuthUser me, @PathVariable Long id,
                                       @RequestBody Map<String, Object> body) {
        cases.resolve(id, s(body, "action"), s(body, "note"), me.username());
        return Map.of("message", "Case resolved.");
    }

    @GetMapping("/disputes")
    public List<Map<String, Object>> disputes() {
        return cases.disputes();
    }

    @PostMapping("/disputes/{recordId}/resolve")
    public Map<String, String> resolveDispute(@AuthenticationPrincipal AuthUser me, @PathVariable Long recordId,
                                              @RequestBody Map<String, Object> body) {
        cases.resolveDispute(recordId, s(body, "outcome"), s(body, "note"), me.username());
        return Map.of("message", "Dispute reviewed.");
    }

    @GetMapping("/watchlist")
    public List<Map<String, Object>> watchlist() {
        return monitoring.watchlist();
    }

    @GetMapping("/policy")
    public Map<String, Object> policy() {
        return policy.all();
    }

    @PutMapping("/policy")
    public Map<String, Object> setPolicy(@AuthenticationPrincipal AuthUser me, @RequestBody Map<String, Object> body) {
        StringBuilder changed = new StringBuilder();
        if (body.containsKey(PolicyService.INTAKE_TOLERANCE)) {
            double v = Double.parseDouble(s(body, PolicyService.INTAKE_TOLERANCE));
            if (v < 0 || v > 50) throw new IllegalArgumentException("Intake tolerance must be between 0 and 50 percent.");
            policy.set(PolicyService.INTAKE_TOLERANCE, String.valueOf(v));
            changed.append("intakeTolerance=").append(v).append(' ');
        }
        if (body.containsKey(PolicyService.SPIKE_THRESHOLD)) {
            double v = Double.parseDouble(s(body, PolicyService.SPIKE_THRESHOLD));
            if (v < 5 || v > 500) throw new IllegalArgumentException("Spike threshold must be between 5 and 500 percent.");
            policy.set(PolicyService.SPIKE_THRESHOLD, String.valueOf(v));
            changed.append("spikeThreshold=").append(v).append(' ');
        }
        if (body.containsKey(PolicyService.CONFIRMATION_MODE)) {
            String v = s(body, PolicyService.CONFIRMATION_MODE);
            if (!List.of("OFF", "OPTIONAL", "MANDATORY").contains(v)) throw new IllegalArgumentException("Student confirmation must be OFF, OPTIONAL or MANDATORY.");
            policy.set(PolicyService.CONFIRMATION_MODE, v);
            changed.append("studentConfirmation=").append(v).append(' ');
        }
        if (body.containsKey(PolicyService.CONFIRMATION_DAYS)) {
            int v = Integer.parseInt(s(body, PolicyService.CONFIRMATION_DAYS));
            if (v < 1 || v > 365) throw new IllegalArgumentException("Deadline must be between 1 and 365 days.");
            policy.set(PolicyService.CONFIRMATION_DAYS, String.valueOf(v));
            changed.append("confirmationDays=").append(v).append(' ');
        }
        audit.log(me.username(), Const.ROLE_ADMIN, "POLICY_CHANGE", "policy", null, changed.toString().trim());
        monitoring.recomputeAlerts();
        return policy.all();
    }

    @GetMapping("/audit")
    public List<AuditEntry> auditLog() {
        return auditRepo.findTop200ByOrderByIdDesc();
    }

    @GetMapping("/audit/verify")
    public Map<String, Object> verifyAudit() {
        return audit.verifyChain();
    }

    @GetMapping("/reports/{type}")
    public ResponseEntity<byte[]> report(@PathVariable String type) {
        String csv = monitoring.reportCsv(type);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + type + ".csv")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csv.getBytes(StandardCharsets.UTF_8));
    }

    @GetMapping("/requesters")
    public List<Requester> requesters() {
        return requesters.findAll();
    }

    @PostMapping("/requesters/{id}/verify")
    public Map<String, String> verifyRequester(@AuthenticationPrincipal AuthUser me, @PathVariable Long id,
                                               @RequestBody Map<String, Object> body) {
        Requester r = requesters.findById(id).orElseThrow(() -> new NoSuchElementException("Requester not found"));
        boolean verify = !"false".equals(String.valueOf(body.getOrDefault("verified", "true")));
        if (verify && (r.organisation == null || r.organisation.isBlank()))
            throw new IllegalArgumentException("Only requesters that declared an organisation can be verified.");
        r.tier = verify ? Const.TIER_VERIFIED : Const.TIER_GENERAL;
        r.verifiedAt = verify ? Instant.now() : null;
        requesters.save(r);
        audit.log(me.username(), Const.ROLE_ADMIN, verify ? "REQUESTER_VERIFIED" : "REQUESTER_UNVERIFIED", "requester#" + id, null, r.organisation);
        return Map.of("message", verify ? "Requester marked as verified organisation." : "Requester set to general tier.");
    }
}
