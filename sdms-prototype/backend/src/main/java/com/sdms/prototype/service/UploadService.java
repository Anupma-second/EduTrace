package com.sdms.prototype.service;

import com.sdms.prototype.domain.*;
import com.sdms.prototype.domain.Repos.*;
import com.sdms.prototype.service.MonitoringService.Counts;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Student-level data intake and the automated validation engine.
 * The same code path serves the dry-run (instant feedback on file selection), the real upload and the JSON API.
 */
@Service
public class UploadService {

    public record IssueView(int row, String apaarId, String severity, String code, String message) {}

    public record Result(Long batchId, boolean dryRun, int totalRows, int created, int updated, int unchanged,
                         int rejected, int warnings, List<IssueView> issues, List<Map<String, Object>> seats) {}

    private record Msg(String code, String text) {}

    private final InstitutionRepo institutions;
    private final CourseRepo courses;
    private final RecordRepo records;
    private final ApaarRepo registry;
    private final DupRepo dups;
    private final IssueRepo issueRepo;
    private final BatchRepo batches;
    private final HistoryRepo history;
    private final MonitoringService monitoring;
    private final PolicyService policy;
    private final AuditService audit;
    private final MailService mail;
    private final String frontendUrl;
    private final EntityManager em;

    public UploadService(InstitutionRepo institutions, CourseRepo courses, RecordRepo records, ApaarRepo registry,
                         DupRepo dups, IssueRepo issueRepo, BatchRepo batches, HistoryRepo history,
                         MonitoringService monitoring, PolicyService policy, AuditService audit, MailService mail,
                         @Value("${app.frontend-url}") String frontendUrl, EntityManager em) {
        this.institutions = institutions;
        this.courses = courses;
        this.records = records;
        this.registry = registry;
        this.dups = dups;
        this.issueRepo = issueRepo;
        this.batches = batches;
        this.history = history;
        this.monitoring = monitoring;
        this.policy = policy;
        this.audit = audit;
        this.mail = mail;
        this.frontendUrl = frontendUrl;
        this.em = em;
    }

    public static String templateCsv() {
        return "apaar_id,name,dob,gender,course_code,admission_year,category,seat_type,residency,admission_mode,status,roll_no\n"
                + "123456789012,Example Student,2008-05-17,FEMALE,BTECH-CSE,2026,GEN,REGULAR,INDIAN,COUNSELLING,ACTIVE,2026CS001\n";
    }

    // ------------------------------------------------------------------ main entry

    @Transactional
    public Result process(Long instId, List<Map<String, String>> rows, boolean dryRun, String actor, String fileName) {
        Institution inst = institutions.findById(instId).orElseThrow();
        Map<String, Course> byCode = new HashMap<>();
        Map<Long, Course> byId = new HashMap<>();
        for (Course c : courses.findByInstitutionId(instId)) {
            byCode.put(c.code.toUpperCase(), c);
            byId.put(c.id, c);
        }
        int year = policy.currentYear();
        String confMode = policy.confirmationMode();

        Map<String, EnrolmentRecord> existing = new HashMap<>();
        for (EnrolmentRecord r : records.findByInstitutionId(instId)) {
            em.detach(r); // detached copies: a dry run can then never change stored data; real saves merge them back
            if (!"WITHDRAWN".equals(r.status)) existing.put(storedKey(r), r);
        }

        Set<String> seen = new HashSet<>();
        List<IssueView> issues = new ArrayList<>();
        List<EnrolmentRecord> toInsert = new ArrayList<>();
        List<EnrolmentRecord> toUpdate = new ArrayList<>();
        Map<EnrolmentRecord, List<RecordHistory>> pendingHistory = new IdentityHashMap<>();
        Map<EnrolmentRecord, List<EnrolmentRecord>> crossDup = new IdentityHashMap<>();
        Map<EnrolmentRecord, String> emailFor = new IdentityHashMap<>();
        Set<Long> touched = new HashSet<>();
        Map<Long, int[]> delta = new HashMap<>();
        int rejected = 0, warnings = 0, unchanged = 0;

        for (int i = 0; i < rows.size(); i++) {
            int rowNo = i + 2;
            Map<String, String> row = rows.get(i);
            String apaarRaw = val(row, "apaar_id");
            List<Msg> errs = new ArrayList<>();
            List<Msg> warns = new ArrayList<>();

            String name = val(row, "name");
            if (name.isEmpty()) errs.add(new Msg("MISSING_FIELD", "Student name is required."));
            LocalDate dob = null;
            try {
                dob = LocalDate.parse(val(row, "dob"));
            } catch (Exception e) {
                errs.add(new Msg("INVALID_DATE", "Date of birth must be in YYYY-MM-DD format."));
            }
            String gender = normGender(val(row, "gender"));
            if (!Const.GENDERS.contains(gender)) errs.add(new Msg("INVALID_VALUE", "Gender must be MALE, FEMALE or OTHER."));
            Course course = byCode.get(val(row, "course_code").toUpperCase());
            if (course == null) errs.add(new Msg("UNKNOWN_COURSE", "Course code '" + val(row, "course_code") + "' is not an approved course of this institution."));
            int admYear = 0;
            try {
                admYear = Integer.parseInt(val(row, "admission_year"));
                if (admYear > year) errs.add(new Msg("FUTURE_YEAR", "Admission year " + admYear + " is later than the current admission cycle (" + year + ")."));
                else if (admYear < year - 10) errs.add(new Msg("INVALID_VALUE", "Admission year " + admYear + " is too old."));
            } catch (Exception e) {
                errs.add(new Msg("INVALID_VALUE", "Admission year must be a number such as " + year + "."));
            }
            String category = val(row, "category").toUpperCase();
            if (!Const.CATEGORIES.contains(category)) errs.add(new Msg("INVALID_VALUE", "Category must be one of " + new TreeSet<>(Const.CATEGORIES) + "."));
            String seat = val(row, "seat_type").toUpperCase();
            if (!Const.SEAT_TYPES.contains(seat)) errs.add(new Msg("INVALID_VALUE", "Seat type must be REGULAR or SUPERNUMERARY."));
            String residency = val(row, "residency").toUpperCase();
            if (!Const.RESIDENCIES.contains(residency)) errs.add(new Msg("INVALID_VALUE", "Residency must be INDIAN, NRI, OCI or FN."));
            String mode = val(row, "admission_mode").toUpperCase();
            if (mode.isEmpty()) mode = "COUNSELLING";
            else if (!Const.ADMISSION_MODES.contains(mode)) errs.add(new Msg("INVALID_VALUE", "Admission mode is not recognised."));
            String status = val(row, "status").toUpperCase();
            if (status.isEmpty()) status = "ACTIVE";
            else if (!Const.STATUSES.contains(status)) errs.add(new Msg("INVALID_VALUE", "Status must be ACTIVE, WITHDRAWN or GRADUATED."));
            String rollNo = val(row, "roll_no");

            String apaar = apaarRaw == null ? "" : apaarRaw.trim();
            if (!errs.isEmpty()) {
                for (Msg m : errs) issues.add(new IssueView(rowNo, apaar, "ERROR", m.code(), m.text()));
                rejected++;
                continue;
            }

            // ---- APAAR validation
            String apaarStatus;
            String email = null;
            if (apaar.isEmpty()) {
                apaarStatus = Const.APAAR_MISSING;
                warns.add(new Msg("MISSING_APAAR", "APAAR ID is missing. The record is kept as 'APAAR pending'; supply a valid ID before the deadline."));
            } else if (!apaar.matches("\\d{12}")) {
                apaarStatus = Const.APAAR_INVALID_FORMAT;
                warns.add(new Msg("INVALID_APAAR", "APAAR ID must be exactly 12 digits."));
            } else {
                ApaarPerson p = registry.findById(apaar).orElse(null);
                if (p == null || !p.active) {
                    apaarStatus = Const.APAAR_NOT_FOUND;
                    warns.add(new Msg("APAAR_NOT_FOUND", "APAAR ID was not found in the registry."));
                } else if (!sameName(p.name, name) || !p.dob.equals(dob)) {
                    apaarStatus = Const.APAAR_MISMATCH;
                    warns.add(new Msg("APAAR_MISMATCH", "Name or date of birth does not match the APAAR registry."));
                } else {
                    apaarStatus = Const.APAAR_VALID;
                    email = p.email;
                }
            }

            boolean formatOk = !(Const.APAAR_MISSING.equals(apaarStatus) || Const.APAAR_INVALID_FORMAT.equals(apaarStatus));
            String key = formatOk ? "A:" + apaar : "R:" + norm(name) + "|" + dob + "|" + course.id + "|" + admYear;

            // ---- duplicates
            if (!seen.add(key)) {
                issues.add(new IssueView(rowNo, apaar, "ERROR", "DUPLICATE_IN_FILE", "The same student appears more than once in this file."));
                rejected++;
                continue;
            }
            EnrolmentRecord ex = existing.get(key);
            if (ex != null && (!ex.courseId.equals(course.id) || ex.admissionYear != admYear)) {
                Course exCourse = byId.get(ex.courseId);
                issues.add(new IssueView(rowNo, apaar, "ERROR", "DUPLICATE_IN_INSTITUTION",
                        "This APAAR ID is already enrolled here in " + (exCourse == null ? "another course" : exCourse.name) + " (" + ex.admissionYear + ")."));
                rejected++;
                continue;
            }

            touched.add(course.id);

            if (ex != null) {
                // ---- update path
                List<RecordHistory> hs = new ArrayList<>();
                boolean changed = false;
                changed |= change(ex, hs, "name", ex.name, name, inst.id, actor, "Re-upload");
                changed |= change(ex, hs, "dob", String.valueOf(ex.dob), String.valueOf(dob), inst.id, actor, "Re-upload");
                changed |= change(ex, hs, "gender", ex.gender, gender, inst.id, actor, "Re-upload");
                changed |= change(ex, hs, "category", ex.category, category, inst.id, actor, "Re-upload");
                changed |= change(ex, hs, "seatType", ex.seatType, seat, inst.id, actor, "Re-upload");
                changed |= change(ex, hs, "residency", ex.residency, residency, inst.id, actor, "Re-upload");
                changed |= change(ex, hs, "admissionMode", ex.admissionMode, mode, inst.id, actor, "Re-upload");
                changed |= change(ex, hs, "status", ex.status, status, inst.id, actor, "Re-upload");
                if (!rollNo.isEmpty()) changed |= change(ex, hs, "rollNo", ex.rollNo, rollNo, inst.id, actor, "Re-upload");
                if (!Objects.equals(ex.apaarStatus, apaarStatus)) { ex.apaarStatus = apaarStatus; changed = true; }
                if (changed) {
                    ex.updatedAt = Instant.now();
                    if (!hs.isEmpty() && (hs.stream().anyMatch(h -> h.fieldName.equals("name") || h.fieldName.equals("dob")))
                            && Const.CONF_VERIFIED.equals(ex.confirmationStatus)) {
                        ex.confirmationStatus = Const.CONF_PENDING; // identity details changed: ask the student again
                    }
                    toUpdate.add(ex);
                    pendingHistory.put(ex, hs);
                } else unchanged++;
                for (Msg m : warns) {
                    issues.add(new IssueView(rowNo, apaar, "WARNING", m.code(), m.text()));
                    warnings++;
                }
                continue;
            }

            // ---- new record
            int age = admYear - dob.getYear();
            if (age < 15 || age > 40) warns.add(new Msg("AGE_INCONSISTENT", "Age at admission would be about " + age + ", which is unusual for this course."));
            if (!"INDIAN".equals(residency) && "REGULAR".equals(seat))
                warns.add(new Msg("QUOTA_CHECK", "Residency is " + residency + " but the seat type is REGULAR; confirm this is the intended quota classification."));
            if ("INDIAN".equals(residency) && "SUPERNUMERARY".equals(seat))
                warns.add(new Msg("QUOTA_CHECK", "Seat type is SUPERNUMERARY for an Indian national; confirm a supernumerary category applies."));

            EnrolmentRecord r = new EnrolmentRecord();
            r.apaarId = apaar.isEmpty() ? null : apaar;
            r.rollNo = rollNo.isEmpty() ? null : rollNo;
            r.name = name;
            r.dob = dob;
            r.gender = gender;
            r.institutionId = inst.id;
            r.courseId = course.id;
            r.admissionYear = admYear;
            r.category = category;
            r.seatType = seat;
            r.residency = residency;
            r.admissionMode = mode;
            r.status = status;
            r.apaarStatus = apaarStatus;
            r.confirmationStatus = "OFF".equals(confMode) ? Const.CONF_NOT_REQUIRED : Const.CONF_PENDING;
            r.createdAt = Instant.now();
            r.updatedAt = r.createdAt;

            if (Const.APAAR_VALID.equals(apaarStatus) && "ACTIVE".equals(status)) {
                List<EnrolmentRecord> others = records.activeElsewhere(apaar, inst.id);
                if (!others.isEmpty()) {
                    crossDup.put(r, others);
                    warns.add(new Msg("CROSS_INSTITUTION_DUPLICATE", "This APAAR ID is also actively enrolled at another institution. A review case will be opened for AICTE."));
                }
            }
            r.flags = warns.stream().map(Msg::code).distinct().collect(Collectors.joining(","));
            for (Msg m : warns) {
                issues.add(new IssueView(rowNo, apaar, "WARNING", m.code(), m.text()));
                warnings++;
            }
            toInsert.add(r);
            if (email != null) emailFor.put(r, email);
            if (!"WITHDRAWN".equals(status) && admYear == year) {
                int[] d = delta.computeIfAbsent(course.id, k -> new int[2]);
                if ("SUPERNUMERARY".equals(seat)) d[1]++; else d[0]++;
            }
        }

        // ---- persist (skipped on a dry run)
        Long batchId = null;
        if (!dryRun) {
            UploadBatch b = new UploadBatch();
            b.institutionId = inst.id;
            b.uploadedBy = actor;
            b.fileName = fileName;
            b.createdAt = Instant.now().truncatedTo(ChronoUnit.MILLIS);
            b.totalRows = rows.size();
            b.accepted = toInsert.size();
            b.updated = toUpdate.size();
            b.rejected = rejected;
            b.warnings = warnings;
            batches.save(b);
            batchId = b.id;

            for (EnrolmentRecord r : toInsert) r.batchId = b.id;
            records.saveAll(toInsert);
            records.saveAll(toUpdate);
            for (var e : pendingHistory.entrySet()) {
                for (RecordHistory h : e.getValue()) {
                    h.recordId = e.getKey().id;
                    history.save(h);
                }
            }

            int emailed = 0;
            for (EnrolmentRecord r : toInsert) {
                List<EnrolmentRecord> others = crossDup.get(r);
                if (others != null) openCase(r, others.get(0), emailFor.get(r));
                if (!"OFF".equals(confMode) && emailFor.containsKey(r) && emailed < 200) {
                    Course c = byId.get(r.courseId);
                    mail.send(emailFor.get(r), "Please confirm your enrolment details",
                            "Dear " + r.name + ",\n\n" + inst.name + " has listed you as enrolled in "
                                    + (c == null ? "a course" : c.name) + " (" + r.admissionYear + " batch).\n\n"
                                    + "Please sign in and confirm the entry, or report an error:\n"
                                    + frontendUrl + "/student/records\n\n"
                                    + "If you do not recognise this enrolment, choose 'Report a mismatch'.\n"
                                    + "No personal data is included in this email.",
                            "CONFIRMATION");
                    emailed++;
                }
            }

            int saved = 0;
            for (IssueView iv : issues) {
                if (saved++ >= 500) break;
                ValidationIssue vi = new ValidationIssue();
                vi.institutionId = inst.id;
                vi.batchId = b.id;
                vi.rowNumber = iv.row();
                vi.apaarId = iv.apaarId();
                vi.severity = iv.severity();
                vi.code = iv.code();
                vi.message = iv.message();
                vi.createdAt = b.createdAt;
                issueRepo.save(vi);
            }
            audit.log(actor, Const.ROLE_INSTITUTION, "UPLOAD", inst.aicteId, null,
                    "file=" + fileName + " rows=" + rows.size() + " created=" + toInsert.size()
                            + " updated=" + toUpdate.size() + " rejected=" + rejected);
            monitoring.recomputeAlerts();
        }

        // ---- intake projection for the courses touched by this file
        Map<Long, Counts> base = monitoring.counts(year);
        double tol = policy.intakeTolerancePct();
        List<Map<String, Object>> seats = new ArrayList<>();
        for (Long cid : touched) {
            Course c = byId.get(cid);
            Counts cc = base.getOrDefault(cid, new Counts());
            int reg = cc.regular, sup = cc.supernumerary;
            if (dryRun && delta.containsKey(cid)) {
                reg += delta.get(cid)[0];
                sup += delta.get(cid)[1];
            }
            Map<String, Object> s = new LinkedHashMap<>();
            s.put("course", c.name);
            s.put("approvedIntake", c.approvedIntake);
            s.put("regularAfterUpload", reg);
            s.put("supernumeraryLimit", c.supernumeraryLimit);
            s.put("supernumeraryAfterUpload", sup);
            boolean over = reg > c.approvedIntake * (1 + tol / 100.0);
            boolean overSup = sup > c.supernumeraryLimit;
            s.put("exceedsIntake", over);
            s.put("exceedsSupernumerary", overSup);
            seats.add(s);
            if (over) {
                issues.add(new IssueView(0, "", "WARNING", "INTAKE_EXCEEDED", c.name + ": " + reg
                        + " regular-seat students against an approved intake of " + c.approvedIntake
                        + ". AICTE will see this as an enrolment alert."));
                warnings++;
            }
            if (overSup) {
                issues.add(new IssueView(0, "", "WARNING", "SUPERNUMERARY_EXCEEDED", c.name + ": " + sup
                        + " supernumerary-seat students against a limit of " + c.supernumeraryLimit + "."));
                warnings++;
            }
        }

        List<IssueView> shown = issues.size() > 1000 ? new ArrayList<>(issues.subList(0, 1000)) : issues;
        return new Result(batchId, dryRun, rows.size(), toInsert.size(), toUpdate.size(), unchanged, rejected,
                warnings, shown, seats);
    }

    // ------------------------------------------------------------------ corrections

    @Transactional
    public EnrolmentRecord correct(Long instId, Long recordId, Map<String, String> changes, String reason, String actor) {
        EnrolmentRecord r = records.findById(recordId).orElseThrow(() -> new IllegalArgumentException("Record not found"));
        if (!instId.equals(r.institutionId)) throw new IllegalArgumentException("Record not found");
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("A reason is required for every correction.");
        List<RecordHistory> hs = new ArrayList<>();
        for (var e : changes.entrySet()) {
            String f = e.getKey();
            String v = e.getValue() == null ? "" : e.getValue().trim();
            switch (f) {
                case "name" -> {
                    require(!v.isEmpty(), "Name cannot be empty.");
                    change(r, hs, "name", r.name, v, instId, actor, reason);
                }
                case "dob" -> change(r, hs, "dob", String.valueOf(r.dob), String.valueOf(parseDate(v)), instId, actor, reason);
                case "category" -> { require(Const.CATEGORIES.contains(v.toUpperCase()), "Invalid category."); change(r, hs, "category", r.category, v.toUpperCase(), instId, actor, reason); }
                case "seatType" -> { require(Const.SEAT_TYPES.contains(v.toUpperCase()), "Invalid seat type."); change(r, hs, "seatType", r.seatType, v.toUpperCase(), instId, actor, reason); }
                case "residency" -> { require(Const.RESIDENCIES.contains(v.toUpperCase()), "Invalid residency."); change(r, hs, "residency", r.residency, v.toUpperCase(), instId, actor, reason); }
                case "status" -> { require(Const.STATUSES.contains(v.toUpperCase()), "Invalid status."); change(r, hs, "status", r.status, v.toUpperCase(), instId, actor, reason); }
                case "admissionMode" -> { require(Const.ADMISSION_MODES.contains(v.toUpperCase()), "Invalid admission mode."); change(r, hs, "admissionMode", r.admissionMode, v.toUpperCase(), instId, actor, reason); }
                case "rollNo" -> change(r, hs, "rollNo", r.rollNo, v, instId, actor, reason);
                case "apaarId" -> {
                    require(v.matches("\\d{12}"), "APAAR ID must be exactly 12 digits.");
                    change(r, hs, "apaarId", r.apaarId, v, instId, actor, reason);
                }
                default -> throw new IllegalArgumentException("Field '" + f + "' cannot be corrected here.");
            }
        }
        if (hs.isEmpty()) return r;
        recheckApaar(r);
        r.updatedAt = Instant.now();
        records.save(r);
        for (RecordHistory h : hs) { h.recordId = r.id; history.save(h); }
        audit.log(actor, Const.ROLE_INSTITUTION, "CORRECTION", "record#" + r.id, r.apaarId,
                hs.stream().map(h -> h.fieldName).collect(Collectors.joining(",")) + " | " + reason);
        monitoring.recomputeAlerts();
        return r;
    }

    private void recheckApaar(EnrolmentRecord r) {
        if (r.apaarId == null || !r.apaarId.matches("\\d{12}")) {
            r.apaarStatus = r.apaarId == null ? Const.APAAR_MISSING : Const.APAAR_INVALID_FORMAT;
            return;
        }
        ApaarPerson p = registry.findById(r.apaarId).orElse(null);
        if (p == null || !p.active) r.apaarStatus = Const.APAAR_NOT_FOUND;
        else if (!sameName(p.name, r.name) || !p.dob.equals(r.dob)) r.apaarStatus = Const.APAAR_MISMATCH;
        else r.apaarStatus = Const.APAAR_VALID;
        if (Const.APAAR_VALID.equals(r.apaarStatus) && "ACTIVE".equals(r.status)) {
            List<EnrolmentRecord> others = records.activeElsewhere(r.apaarId, r.institutionId);
            if (!others.isEmpty()) openCase(r, others.get(0), p.email);
        }
    }

    // ------------------------------------------------------------------ helpers

    private void openCase(EnrolmentRecord mine, EnrolmentRecord other, String studentEmail) {
        if (mine.id == null) records.save(mine);
        if (!dups.findByApaarIdAndStatus(mine.apaarId, "OPEN").isEmpty()) return;
        DuplicateCase c = new DuplicateCase();
        c.apaarId = mine.apaarId;
        c.recordAId = other.id;
        c.recordBId = mine.id;
        c.institutionAId = other.institutionId;
        c.institutionBId = mine.institutionId;
        c.status = "OPEN";
        c.createdAt = Instant.now();
        c.deadline = c.createdAt.plus(14, ChronoUnit.DAYS);
        dups.save(c);
        mine.flags = addFlag(mine.flags, "DUPLICATE_ENROLMENT");
        other.flags = addFlag(other.flags, "DUPLICATE_ENROLMENT");
        records.save(mine);
        records.save(other);
        audit.log("system", "SYSTEM", "DUPLICATE_CASE_OPENED", "case#" + c.id, mine.apaarId,
                "institutions " + other.institutionId + " and " + mine.institutionId);
        if (studentEmail != null) {
            mail.send(studentEmail, "Your APAAR ID appears in two enrolments",
                    "Your APAAR ID is listed as actively enrolled at two institutions. "
                            + "Both institutions and AICTE have been asked to resolve this within 14 days.\n"
                            + "You can see the details after signing in: " + frontendUrl + "/student/records",
                    "DUPLICATE_ENROLMENT");
        }
    }

    private static String addFlag(String flags, String f) {
        if (flags == null || flags.isEmpty()) return f;
        return flags.contains(f) ? flags : flags + "," + f;
    }

    private boolean change(EnrolmentRecord r, List<RecordHistory> hs, String field, String oldV, String newV,
                           Long instId, String actor, String reason) {
        if (Objects.equals(oldV, newV)) return false;
        RecordHistory h = new RecordHistory();
        h.institutionId = instId;
        h.fieldName = field;
        h.oldValue = oldV;
        h.newValue = newV;
        h.reason = reason;
        h.changedBy = actor;
        h.changedAt = Instant.now();
        hs.add(h);
        switch (field) {
            case "name" -> r.name = newV;
            case "dob" -> r.dob = LocalDate.parse(newV);
            case "gender" -> r.gender = newV;
            case "category" -> r.category = newV;
            case "seatType" -> r.seatType = newV;
            case "residency" -> r.residency = newV;
            case "admissionMode" -> r.admissionMode = newV;
            case "status" -> r.status = newV;
            case "rollNo" -> r.rollNo = newV;
            case "apaarId" -> r.apaarId = newV;
            default -> { }
        }
        return true;
    }

    private static void require(boolean ok, String msg) {
        if (!ok) throw new IllegalArgumentException(msg);
    }

    private static LocalDate parseDate(String v) {
        try {
            return LocalDate.parse(v);
        } catch (Exception e) {
            throw new IllegalArgumentException("Date of birth must be in YYYY-MM-DD format.");
        }
    }

    private static String val(Map<String, String> row, String k) {
        String v = row.get(k);
        return v == null ? "" : v.trim();
    }

    private static String normGender(String g) {
        String u = g.trim().toUpperCase();
        return switch (u) {
            case "M" -> "MALE";
            case "F" -> "FEMALE";
            case "O" -> "OTHER";
            default -> u;
        };
    }

    static String norm(String s) {
        return s == null ? "" : s.toLowerCase().replaceAll("[^a-z ]", "").replaceAll("\\s+", " ").trim();
    }

    private static boolean sameName(String a, String b) {
        return norm(a).equals(norm(b));
    }

    private static String storedKey(EnrolmentRecord r) {
        boolean formatOk = r.apaarStatus != null
                && !Const.APAAR_MISSING.equals(r.apaarStatus) && !Const.APAAR_INVALID_FORMAT.equals(r.apaarStatus);
        return formatOk ? "A:" + r.apaarId : "R:" + norm(r.name) + "|" + r.dob + "|" + r.courseId + "|" + r.admissionYear;
    }
}
