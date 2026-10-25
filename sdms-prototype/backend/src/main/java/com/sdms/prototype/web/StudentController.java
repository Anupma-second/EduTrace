package com.sdms.prototype.web;

import com.sdms.prototype.domain.*;
import com.sdms.prototype.domain.Repos.*;
import com.sdms.prototype.security.AuthUser;
import com.sdms.prototype.service.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/** The student's own view: confirm records, decide access requests, see who looked at their data. */
@RestController
@RequestMapping("/api/student")
public class StudentController {
    private final RecordRepo records;
    private final InstitutionRepo institutions;
    private final CourseRepo courses;
    private final ApaarRepo registry;
    private final DupRepo dups;
    private final AuditRepo auditRepo;
    private final CaseService cases;
    private final AccessService access;

    public StudentController(RecordRepo records, InstitutionRepo institutions, CourseRepo courses, ApaarRepo registry,
                             DupRepo dups, AuditRepo auditRepo, CaseService cases, AccessService access) {
        this.records = records;
        this.institutions = institutions;
        this.courses = courses;
        this.registry = registry;
        this.dups = dups;
        this.auditRepo = auditRepo;
        this.cases = cases;
        this.access = access;
    }

    @GetMapping("/records")
    public Map<String, Object> myRecords(@AuthenticationPrincipal AuthUser me) {
        ApaarPerson p = registry.findById(me.apaarId()).orElseThrow();
        Map<Long, Institution> inst = institutions.findAll().stream().collect(Collectors.toMap(i -> i.id, i -> i));
        Map<Long, Course> crs = courses.findAll().stream().collect(Collectors.toMap(c -> c.id, c -> c));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (EnrolmentRecord r : records.findByApaarId(me.apaarId())) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.id);
            m.put("institution", inst.containsKey(r.institutionId) ? inst.get(r.institutionId).name : "");
            m.put("course", crs.containsKey(r.courseId) ? crs.get(r.courseId).name : "");
            m.put("admissionYear", r.admissionYear);
            m.put("rollNo", r.rollNo);
            m.put("status", r.status);
            m.put("seatType", r.seatType);
            m.put("category", r.category);
            m.put("confirmationStatus", r.confirmationStatus);
            m.put("confirmationNote", r.confirmationNote);
            m.put("duplicateFlag", r.flags != null && r.flags.contains("DUPLICATE_ENROLMENT"));
            rows.add(m);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("apaarId", p.apaarId);
        out.put("name", p.name);
        out.put("dob", p.dob);
        out.put("email", p.email);
        out.put("records", rows);
        out.put("openDuplicateCases", dups.findByApaarIdAndStatus(me.apaarId(), "OPEN").size());
        return out;
    }

    @PostMapping("/records/{id}/confirm")
    public Map<String, String> confirm(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        cases.confirm(me.apaarId(), id);
        return Map.of("message", "Thank you. Your enrolment is now marked as confirmed.");
    }

    @PostMapping("/records/{id}/dispute")
    public Map<String, String> dispute(@AuthenticationPrincipal AuthUser me, @PathVariable Long id,
                                       @RequestBody Map<String, Object> body) {
        cases.dispute(me.apaarId(), id, String.valueOf(body.getOrDefault("reason", "")));
        return Map.of("message", "Your report was sent to AICTE for review.");
    }

    @GetMapping("/certificate/{id}")
    public Map<String, Object> certificate(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        EnrolmentRecord r = records.findById(id).orElseThrow(() -> new NoSuchElementException("Record not found"));
        if (!me.apaarId().equals(r.apaarId)) throw new NoSuchElementException("Record not found");
        Institution in = institutions.findById(r.institutionId).orElseThrow();
        Course c = courses.findById(r.courseId).orElseThrow();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "enrolment-record");
        payload.put("apaarId", r.apaarId);
        payload.put("enrolmentNo", r.rollNo);
        payload.put("institution", in.name);
        payload.put("course", c.name);
        payload.put("admissionYear", r.admissionYear);
        Map<String, Object> out = new LinkedHashMap<>(access.signed(payload));
        out.put("studentName", r.name);
        return out;
    }

    // ---------------------------------------------------------------- Student-Controlled Access

    @GetMapping("/requests")
    public List<Map<String, Object>> requests(@AuthenticationPrincipal AuthUser me) {
        return access.listForStudent(me.apaarId());
    }

    @PostMapping("/requests/{id}/decision")
    @SuppressWarnings("unchecked")
    public Map<String, Object> decide(@AuthenticationPrincipal AuthUser me, @PathVariable Long id,
                                      @RequestBody Map<String, Object> body) {
        boolean approve = Boolean.TRUE.equals(body.get("approve"));
        List<String> fields = body.get("fields") instanceof List<?> l ? (List<String>) l : null;
        Integer days = body.get("durationDays") instanceof Number n ? n.intValue() : null;
        return access.decide(me.apaarId(), id, approve, fields, days);
    }

    @PostMapping("/requests/{id}/revoke")
    public Map<String, Object> revoke(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        return access.revoke(me.apaarId(), id);
    }

    @GetMapping("/blocked")
    public List<Map<String, Object>> blocked(@AuthenticationPrincipal AuthUser me) {
        return access.blockedList(me.apaarId());
    }

    @PostMapping("/blocks")
    public Map<String, String> block(@AuthenticationPrincipal AuthUser me, @RequestBody Map<String, Object> body) {
        access.block(me.apaarId(), Long.valueOf(String.valueOf(body.get("requesterId"))));
        return Map.of("message", "Requester blocked. Pending and active approvals from them were withdrawn.");
    }

    @DeleteMapping("/blocks/{id}")
    public Map<String, String> unblock(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        access.unblock(me.apaarId(), id);
        return Map.of("message", "Requester unblocked.");
    }

    @GetMapping("/access-log")
    public List<AuditEntry> accessLog(@AuthenticationPrincipal AuthUser me) {
        return auditRepo.findTop100BySubjectApaarOrderByIdDesc(me.apaarId());
    }
}
