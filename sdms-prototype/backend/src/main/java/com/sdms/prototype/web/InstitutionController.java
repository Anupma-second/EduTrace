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
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/** Everything an institution can do. Every query is scoped to the caller's own institutionId. */
@RestController
@RequestMapping("/api/institution")
public class InstitutionController {
    private final UploadService uploads;
    private final MonitoringService monitoring;
    private final CaseService cases;
    private final RecordRepo records;
    private final CourseRepo courses;
    private final InstitutionRepo institutions;
    private final IssueRepo issues;
    private final BatchRepo batches;
    private final HistoryRepo history;
    private final PolicyService policy;

    public InstitutionController(UploadService uploads, MonitoringService monitoring, CaseService cases,
                                 RecordRepo records, CourseRepo courses, InstitutionRepo institutions,
                                 IssueRepo issues, BatchRepo batches, HistoryRepo history, PolicyService policy) {
        this.uploads = uploads;
        this.monitoring = monitoring;
        this.cases = cases;
        this.records = records;
        this.courses = courses;
        this.institutions = institutions;
        this.issues = issues;
        this.batches = batches;
        this.history = history;
        this.policy = policy;
    }

    @GetMapping("/summary")
    public Map<String, Object> summary(@AuthenticationPrincipal AuthUser me) {
        Institution in = institutions.findById(me.institutionId()).orElseThrow();
        int year = policy.currentYear();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("institution", in);
        m.put("admissionYear", year);
        m.put("seatTracker", monitoring.seatTracker(in.id));
        m.put("records", records.countByInstitutionIdAndAdmissionYear(in.id, year));
        m.put("apaarIssues", records.countByInstitutionIdAndAdmissionYearAndApaarStatusNot(in.id, year, Const.APAAR_VALID));
        Map<String, Long> conf = new LinkedHashMap<>();
        for (String s : List.of(Const.CONF_VERIFIED, Const.CONF_PENDING, Const.CONF_DISPUTED, Const.CONF_UNCONFIRMED, Const.CONF_REVIEWED, Const.CONF_NOT_REQUIRED)) {
            conf.put(s, records.countByInstitutionIdAndAdmissionYearAndConfirmationStatus(in.id, year, s));
        }
        m.put("confirmation", conf);
        m.put("confirmationMode", policy.confirmationMode());
        m.put("batches", batches.findTop10ByInstitutionIdOrderByIdDesc(in.id));
        m.put("alerts", monitoring.alertViews(in.id));
        return m;
    }

    @GetMapping("/students")
    public Map<String, Object> students(@AuthenticationPrincipal AuthUser me,
                                        @RequestParam(required = false) String q,
                                        @RequestParam(required = false) Integer year,
                                        @RequestParam(required = false) String issue,
                                        @RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "25") int size) {
        Map<Long, Course> crs = courses.findByInstitutionId(me.institutionId()).stream().collect(Collectors.toMap(c -> c.id, c -> c));
        String needle = q == null ? "" : q.trim().toLowerCase();
        List<EnrolmentRecord> all = records.findByInstitutionId(me.institutionId()).stream()
                .filter(r -> year == null || r.admissionYear == year)
                .filter(r -> issue == null || issue.isEmpty() || ("APAAR".equals(issue) && !Const.APAAR_VALID.equals(r.apaarStatus))
                        || ("DISPUTED".equals(issue) && Const.CONF_DISPUTED.equals(r.confirmationStatus))
                        || ("DUPLICATE".equals(issue) && r.flags != null && r.flags.contains("DUPLICATE_ENROLMENT")))
                .filter(r -> needle.isEmpty() || (r.name != null && r.name.toLowerCase().contains(needle))
                        || (r.apaarId != null && r.apaarId.contains(needle))
                        || (r.rollNo != null && r.rollNo.toLowerCase().contains(needle)))
                .sorted(Comparator.comparing((EnrolmentRecord r) -> r.id).reversed())
                .collect(Collectors.toList());
        int from = Math.min(Math.max(page, 0) * size, all.size());
        int to = Math.min(from + size, all.size());
        List<Map<String, Object>> rows = new ArrayList<>();
        for (EnrolmentRecord r : all.subList(from, to)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.id);
            m.put("apaarId", r.apaarId);
            m.put("rollNo", r.rollNo);
            m.put("name", r.name);
            m.put("dob", r.dob);
            m.put("gender", r.gender);
            m.put("course", crs.containsKey(r.courseId) ? crs.get(r.courseId).name : "");
            m.put("admissionYear", r.admissionYear);
            m.put("category", r.category);
            m.put("seatType", r.seatType);
            m.put("residency", r.residency);
            m.put("admissionMode", r.admissionMode);
            m.put("status", r.status);
            m.put("apaarStatus", r.apaarStatus);
            m.put("confirmationStatus", r.confirmationStatus);
            m.put("confirmationNote", r.confirmationNote);
            m.put("flags", r.flags);
            rows.add(m);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("total", all.size());
        out.put("page", page);
        out.put("size", size);
        out.put("rows", rows);
        return out;
    }

    @GetMapping("/students/{id}/history")
    public List<RecordHistory> history(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        EnrolmentRecord r = records.findById(id).orElseThrow(() -> new NoSuchElementException("Record not found"));
        if (!me.institutionId().equals(r.institutionId)) throw new NoSuchElementException("Record not found");
        return history.findByRecordIdOrderByIdDesc(id);
    }

    @PatchMapping("/students/{id}")
    public Map<String, Object> correct(@AuthenticationPrincipal AuthUser me, @PathVariable Long id,
                                       @RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        Map<String, Object> raw = (Map<String, Object>) body.getOrDefault("changes", Map.of());
        Map<String, String> changes = new LinkedHashMap<>();
        raw.forEach((k, v) -> changes.put(k, v == null ? "" : String.valueOf(v)));
        EnrolmentRecord r = uploads.correct(me.institutionId(), id, changes, String.valueOf(body.getOrDefault("reason", "")), me.username());
        return Map.of("id", r.id, "apaarStatus", r.apaarStatus, "status", r.status);
    }

    @PostMapping("/upload")
    public UploadService.Result upload(@AuthenticationPrincipal AuthUser me,
                                       @RequestParam("file") MultipartFile file,
                                       @RequestParam(defaultValue = "false") boolean dryRun) throws Exception {
        if (file.isEmpty()) throw new IllegalArgumentException("The file is empty.");
        String text = new String(file.getBytes(), StandardCharsets.UTF_8);
        List<Map<String, String>> rows = CsvUtil.toMaps(CsvUtil.parse(text));
        if (rows.isEmpty()) throw new IllegalArgumentException("No data rows found. The first row must be the column header.");
        if (rows.size() > 5000) throw new IllegalArgumentException("Upload at most 5000 rows at a time.");
        for (String col : List.of("apaar_id", "name", "dob", "gender", "course_code", "admission_year", "category", "seat_type", "residency")) {
            if (!rows.get(0).containsKey(col)) throw new IllegalArgumentException("Missing required column: " + col + ". Download the template for the exact layout.");
        }
        return uploads.process(me.institutionId(), rows, dryRun, me.username(), file.getOriginalFilename());
    }

    /** JSON endpoint for institution ERP systems. Same validation as the file upload. */
    @PostMapping("/students/bulk")
    @SuppressWarnings("unchecked")
    public UploadService.Result bulk(@AuthenticationPrincipal AuthUser me, @RequestBody Map<String, Object> body) {
        List<Map<String, Object>> in = (List<Map<String, Object>>) body.getOrDefault("rows", List.of());
        if (in.isEmpty()) throw new IllegalArgumentException("Send a non-empty 'rows' array.");
        if (in.size() > 5000) throw new IllegalArgumentException("Send at most 5000 rows per call.");
        List<Map<String, String>> rows = new ArrayList<>();
        for (Map<String, Object> r : in) {
            Map<String, String> m = new LinkedHashMap<>();
            r.forEach((k, v) -> m.put(k.toLowerCase(), v == null ? "" : String.valueOf(v)));
            rows.add(m);
        }
        return uploads.process(me.institutionId(), rows, Boolean.TRUE.equals(body.get("dryRun")), me.username(), "API");
    }

    @GetMapping("/template")
    public ResponseEntity<byte[]> template() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=student-upload-template.csv")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(UploadService.templateCsv().getBytes(StandardCharsets.UTF_8));
    }

    @GetMapping("/courses")
    public List<Course> courses(@AuthenticationPrincipal AuthUser me) {
        return courses.findByInstitutionId(me.institutionId());
    }

    @GetMapping("/issues")
    public List<ValidationIssue> issues(@AuthenticationPrincipal AuthUser me) {
        return issues.findTop300ByInstitutionIdOrderByIdDesc(me.institutionId());
    }

    @GetMapping("/duplicates")
    public List<Map<String, Object>> duplicates(@AuthenticationPrincipal AuthUser me) {
        return cases.duplicateViews(me.institutionId());
    }

    @PostMapping("/duplicates/{id}/respond")
    public Map<String, String> respond(@AuthenticationPrincipal AuthUser me, @PathVariable Long id,
                                       @RequestBody Map<String, Object> body) {
        cases.respond(id, me.institutionId(), String.valueOf(body.getOrDefault("note", "")), me.username());
        return Map.of("message", "Response recorded.");
    }
}
