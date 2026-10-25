package com.sdms.prototype.service;

import com.sdms.prototype.domain.*;
import com.sdms.prototype.domain.Repos.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/** Duplicate-enrolment resolution, student confirmation/disputes and scheduled maintenance. */
@Service
public class CaseService {
    private final DupRepo dups;
    private final RecordRepo records;
    private final InstitutionRepo institutions;
    private final CourseRepo courses;
    private final AuditService audit;
    private final MonitoringService monitoring;
    private final PolicyService policy;
    private final AccessService accessService;

    public CaseService(DupRepo dups, RecordRepo records, InstitutionRepo institutions, CourseRepo courses,
                       AuditService audit, MonitoringService monitoring, PolicyService policy,
                       AccessService accessService) {
        this.dups = dups;
        this.records = records;
        this.institutions = institutions;
        this.courses = courses;
        this.audit = audit;
        this.monitoring = monitoring;
        this.policy = policy;
        this.accessService = accessService;
    }

    // ------------------------------------------------------------------ duplicates

    public List<Map<String, Object>> duplicateViews(Long institutionIdOrNull) {
        Map<Long, Institution> inst = institutions.findAll().stream().collect(Collectors.toMap(i -> i.id, i -> i));
        Map<Long, Course> crs = courses.findAll().stream().collect(Collectors.toMap(c -> c.id, c -> c));
        List<DuplicateCase> list = institutionIdOrNull == null
                ? dups.findAllByOrderByIdDesc()
                : dups.findByInstitutionAIdOrInstitutionBIdOrderByIdDesc(institutionIdOrNull, institutionIdOrNull);
        List<Map<String, Object>> out = new ArrayList<>();
        for (DuplicateCase c : list) {
            EnrolmentRecord a = records.findById(c.recordAId).orElse(null);
            EnrolmentRecord b = records.findById(c.recordBId).orElse(null);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", c.id);
            m.put("apaarId", c.apaarId);
            m.put("status", c.status);
            m.put("resolution", c.resolution);
            m.put("note", c.note);
            m.put("createdAt", c.createdAt);
            m.put("deadline", c.deadline);
            m.put("overdue", "OPEN".equals(c.status) && c.deadline != null && Instant.now().isAfter(c.deadline));
            m.put("institutionA", inst.containsKey(c.institutionAId) ? inst.get(c.institutionAId).name : "");
            m.put("institutionB", inst.containsKey(c.institutionBId) ? inst.get(c.institutionBId).name : "");
            m.put("institutionAId", c.institutionAId);
            m.put("institutionBId", c.institutionBId);
            m.put("courseA", a != null && crs.containsKey(a.courseId) ? crs.get(a.courseId).name + " (" + a.admissionYear + ")" : "");
            m.put("courseB", b != null && crs.containsKey(b.courseId) ? crs.get(b.courseId).name + " (" + b.admissionYear + ")" : "");
            m.put("recordAId", c.recordAId);
            m.put("recordBId", c.recordBId);
            m.put("responseA", c.responseA);
            m.put("responseB", c.responseB);
            out.add(m);
        }
        return out;
    }

    @Transactional
    public void respond(Long caseId, Long institutionId, String note, String actor) {
        DuplicateCase c = dups.findById(caseId).orElseThrow(() -> new NoSuchElementException("Case not found"));
        if (note == null || note.isBlank()) throw new IllegalArgumentException("Enter a response.");
        String n = note.length() > 480 ? note.substring(0, 480) : note;
        if (institutionId.equals(c.institutionAId)) c.responseA = n;
        else if (institutionId.equals(c.institutionBId)) c.responseB = n;
        else throw new NoSuchElementException("Case not found");
        dups.save(c);
        audit.log(actor, Const.ROLE_INSTITUTION, "DUPLICATE_RESPONSE", "case#" + c.id, c.apaarId, n);
    }

    @Transactional
    public void resolve(Long caseId, String action, String note, String actor) {
        DuplicateCase c = dups.findById(caseId).orElseThrow(() -> new NoSuchElementException("Case not found"));
        if (!"OPEN".equals(c.status)) throw new IllegalArgumentException("Case is already resolved.");
        EnrolmentRecord a = records.findById(c.recordAId).orElseThrow();
        EnrolmentRecord b = records.findById(c.recordBId).orElseThrow();
        switch (action) {
            case "KEEP_A" -> withdraw(b, "Withdrawn on resolution of duplicate case #" + c.id);
            case "KEEP_B" -> withdraw(a, "Withdrawn on resolution of duplicate case #" + c.id);
            case "BOTH_VALID" -> { }
            default -> throw new IllegalArgumentException("Unknown action.");
        }
        a.flags = removeFlag(a.flags, "DUPLICATE_ENROLMENT");
        b.flags = removeFlag(b.flags, "DUPLICATE_ENROLMENT");
        records.save(a);
        records.save(b);
        c.status = "RESOLVED";
        c.resolution = action;
        c.note = note;
        c.resolvedAt = Instant.now();
        dups.save(c);
        audit.log(actor, Const.ROLE_ADMIN, "DUPLICATE_RESOLVED", "case#" + c.id, c.apaarId, action + (note == null ? "" : " | " + note));
        monitoring.recomputeAlerts();
    }

    private void withdraw(EnrolmentRecord r, String why) {
        r.status = "WITHDRAWN";
        r.confirmationNote = why;
        r.updatedAt = Instant.now();
    }

    private static String removeFlag(String flags, String f) {
        if (flags == null) return null;
        String s = Arrays.stream(flags.split(",")).filter(x -> !x.equals(f) && !x.isEmpty()).collect(Collectors.joining(","));
        return s.isEmpty() ? null : s;
    }

    // ------------------------------------------------------------------ student confirmation

    @Transactional
    public void confirm(String apaarId, Long recordId) {
        EnrolmentRecord r = ownRecord(apaarId, recordId);
        r.confirmationStatus = Const.CONF_VERIFIED;
        r.confirmationNote = null;
        r.updatedAt = Instant.now();
        records.save(r);
        audit.log(apaarId, Const.ROLE_STUDENT, "STUDENT_CONFIRMED", "record#" + r.id, apaarId, "student confirmed enrolment");
    }

    @Transactional
    public void dispute(String apaarId, Long recordId, String reason) {
        EnrolmentRecord r = ownRecord(apaarId, recordId);
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("Please say what is wrong.");
        r.confirmationStatus = Const.CONF_DISPUTED;
        r.confirmationNote = reason.length() > 480 ? reason.substring(0, 480) : reason;
        r.updatedAt = Instant.now();
        records.save(r);
        audit.log(apaarId, Const.ROLE_STUDENT, "STUDENT_DISPUTED", "record#" + r.id, apaarId, r.confirmationNote);
    }

    private EnrolmentRecord ownRecord(String apaarId, Long recordId) {
        EnrolmentRecord r = records.findById(recordId).orElseThrow(() -> new NoSuchElementException("Record not found"));
        if (!apaarId.equals(r.apaarId)) throw new NoSuchElementException("Record not found");
        return r;
    }

    public List<Map<String, Object>> disputes() {
        Map<Long, Institution> inst = institutions.findAll().stream().collect(Collectors.toMap(i -> i.id, i -> i));
        Map<Long, Course> crs = courses.findAll().stream().collect(Collectors.toMap(c -> c.id, c -> c));
        List<Map<String, Object>> out = new ArrayList<>();
        for (EnrolmentRecord r : records.findByConfirmationStatus(Const.CONF_DISPUTED)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("recordId", r.id);
            m.put("apaarId", r.apaarId);
            m.put("studentName", r.name);
            m.put("institution", inst.containsKey(r.institutionId) ? inst.get(r.institutionId).name : "");
            m.put("course", crs.containsKey(r.courseId) ? crs.get(r.courseId).name : "");
            m.put("admissionYear", r.admissionYear);
            m.put("studentSays", r.confirmationNote);
            m.put("updatedAt", r.updatedAt);
            out.add(m);
        }
        return out;
    }

    @Transactional
    public void resolveDispute(Long recordId, String outcome, String note, String actor) {
        EnrolmentRecord r = records.findById(recordId).orElseThrow(() -> new NoSuchElementException("Record not found"));
        if (!Const.CONF_DISPUTED.equals(r.confirmationStatus)) throw new IllegalArgumentException("Record is not disputed.");
        switch (outcome) {
            case "DISMISSED" -> r.confirmationStatus = Const.CONF_REVIEWED;
            case "RETURN_TO_INSTITUTION" -> r.confirmationStatus = Const.CONF_PENDING;
            default -> throw new IllegalArgumentException("Unknown outcome.");
        }
        r.confirmationNote = note;
        r.updatedAt = Instant.now();
        records.save(r);
        audit.log(actor, Const.ROLE_ADMIN, "DISPUTE_RESOLVED", "record#" + r.id, r.apaarId, outcome + (note == null ? "" : " | " + note));
    }

    // ------------------------------------------------------------------ maintenance

    @Transactional
    public Map<String, Integer> maintenance() {
        int unconfirmed = 0;
        if (!"OFF".equals(policy.confirmationMode())) {
            Instant cutoff = Instant.now().minus(policy.confirmationDays(), ChronoUnit.DAYS);
            for (EnrolmentRecord r : records.findByConfirmationStatus(Const.CONF_PENDING)) {
                if (r.createdAt != null && r.createdAt.isBefore(cutoff)) {
                    r.confirmationStatus = Const.CONF_UNCONFIRMED;
                    records.save(r);
                    unconfirmed++;
                }
            }
        }
        int expired = accessService.expireAll();
        Map<String, Integer> m = new LinkedHashMap<>();
        m.put("markedUnconfirmed", unconfirmed);
        m.put("accessRequestsExpired", expired);
        return m;
    }
}
