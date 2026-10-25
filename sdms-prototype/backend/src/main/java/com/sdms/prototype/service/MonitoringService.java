package com.sdms.prototype.service;

import com.sdms.prototype.domain.*;
import com.sdms.prototype.domain.Repos.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/** Seat tracking, enrolment alerts with explanations, watchlist, overview numbers and reports. */
@Service
public class MonitoringService {

    public static class Counts {
        public int regular, supernumerary, nri, oci, fn;
        public int total() { return regular + supernumerary; }
    }

    private final InstitutionRepo institutions;
    private final CourseRepo courses;
    private final RecordRepo records;
    private final AlertRepo alerts;
    private final DupRepo dups;
    private final PolicyService policy;

    public MonitoringService(InstitutionRepo institutions, CourseRepo courses, RecordRepo records,
                             AlertRepo alerts, DupRepo dups, PolicyService policy) {
        this.institutions = institutions;
        this.courses = courses;
        this.records = records;
        this.alerts = alerts;
        this.dups = dups;
        this.policy = policy;
    }

    public Map<Long, Counts> counts(int year) {
        Map<Long, Counts> m = new HashMap<>();
        for (Object[] r : records.countsByCourse(year)) {
            Long cid = (Long) r[0];
            String seat = (String) r[1];
            String res = (String) r[2];
            int n = ((Number) r[3]).intValue();
            Counts c = m.computeIfAbsent(cid, k -> new Counts());
            if ("SUPERNUMERARY".equals(seat)) c.supernumerary += n; else c.regular += n;
            if ("NRI".equals(res)) c.nri += n;
            else if ("OCI".equals(res)) c.oci += n;
            else if ("FN".equals(res)) c.fn += n;
        }
        return m;
    }

    // ---------------------------------------------------------------- alerts

    @Transactional
    public int recomputeAlerts() {
        int year = policy.currentYear();
        Map<Long, Counts> cur = counts(year);
        Map<Long, Counts> prev = counts(year - 1);
        double tol = policy.intakeTolerancePct();
        double spike = policy.spikeThresholdPct();
        Map<Long, Institution> inst = institutions.findAll().stream()
                .collect(Collectors.toMap(i -> i.id, i -> i));
        Set<String> live = new HashSet<>();

        for (Course c : courses.findAll()) {
            Counts cc = cur.getOrDefault(c.id, new Counts());
            Counts pc = prev.getOrDefault(c.id, new Counts());
            Institution in = inst.get(c.institutionId);
            if (in == null) continue;
            String label = in.name + " - " + c.name;

            if (cc.regular > c.approvedIntake * (1 + tol / 100.0)) {
                double pct = (cc.regular - c.approvedIntake) * 100.0 / c.approvedIntake;
                String msg = String.format(Locale.ROOT,
                        "%s: %d regular-seat students are enrolled against an approved intake of %d (%.1f%% above).%s",
                        label, cc.regular, c.approvedIntake, pct,
                        tol > 0 ? String.format(Locale.ROOT, " Policy tolerance is %.1f%%.", tol) : "");
                upsert(c, year, "INTAKE_EXCEEDED", "HIGH", cc.regular, c.approvedIntake, msg, live);
            }
            if (cc.supernumerary > c.supernumeraryLimit) {
                String msg = String.format(Locale.ROOT,
                        "%s: %d supernumerary-seat students are enrolled against a permitted limit of %d.",
                        label, cc.supernumerary, c.supernumeraryLimit);
                upsert(c, year, "SUPERNUMERARY_EXCEEDED", "MEDIUM", cc.supernumerary, c.supernumeraryLimit, msg, live);
            }
            if (pc.regular >= 20 && cc.regular > pc.regular * (1 + spike / 100.0)) {
                double pct = (cc.regular - pc.regular) * 100.0 / pc.regular;
                String msg = String.format(Locale.ROOT,
                        "%s: %d regular-seat students enrolled in %d against %d in %d (%.0f%% higher than last year; alert threshold is %.0f%%).",
                        label, cc.regular, year, pc.regular, year - 1, pct, spike);
                upsert(c, year, "ENROLMENT_SPIKE", "MEDIUM", cc.regular, pc.regular, msg, live);
            }
        }
        for (Alert a : alerts.findByAdmissionYearOrderByIdDesc(year)) {
            String key = a.courseId + "|" + a.type;
            if (!live.contains(key) && ("OPEN".equals(a.status) || "REVIEWED".equals(a.status))) {
                a.status = "AUTO_CLOSED";
                a.note = "Condition no longer holds after the latest data.";
                a.updatedAt = Instant.now();
                alerts.save(a);
            }
        }
        return live.size();
    }

    private void upsert(Course c, int year, String type, String severity, int cur, int base, String msg, Set<String> live) {
        live.add(c.id + "|" + type);
        List<Alert> existing = alerts.findByCourseIdAndTypeAndAdmissionYear(c.id, type, year);
        Alert a = existing.isEmpty() ? new Alert() : existing.get(0);
        if (existing.isEmpty()) {
            a.institutionId = c.institutionId;
            a.courseId = c.id;
            a.admissionYear = year;
            a.type = type;
            a.status = "OPEN";
            a.createdAt = Instant.now();
        } else if ("AUTO_CLOSED".equals(a.status)) {
            a.status = "OPEN";
        }
        a.severity = severity;
        a.currentValue = cur;
        a.baselineValue = base;
        a.message = msg;
        a.updatedAt = Instant.now();
        alerts.save(a);
    }

    // ---------------------------------------------------------------- seat tracker

    public List<Map<String, Object>> seatTracker(Long institutionId) {
        Map<Long, Counts> cur = counts(policy.currentYear());
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Course c : courses.findByInstitutionId(institutionId)) {
            Counts cc = cur.getOrDefault(c.id, new Counts());
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("courseId", c.id);
            r.put("code", c.code);
            r.put("name", c.name);
            r.put("approvedIntake", c.approvedIntake);
            r.put("supernumeraryLimit", c.supernumeraryLimit);
            r.put("regularFilled", cc.regular);
            r.put("supernumeraryFilled", cc.supernumerary);
            r.put("remainingRegular", Math.max(0, c.approvedIntake - cc.regular));
            r.put("nri", cc.nri);
            r.put("oci", cc.oci);
            r.put("fn", cc.fn);
            r.put("fillPct", c.approvedIntake == 0 ? 0 : Math.round(cc.regular * 1000.0 / c.approvedIntake) / 10.0);
            String status;
            if (cc.total() == 0) status = "NO_DATA";
            else if (cc.regular > c.approvedIntake || cc.supernumerary > c.supernumeraryLimit) status = "OVER_LIMIT";
            else if (cc.regular == c.approvedIntake) status = "FULL";
            else status = "OPEN";
            r.put("status", status);
            rows.add(r);
        }
        return rows;
    }

    // ---------------------------------------------------------------- admin views

    public Map<String, Object> overview() {
        int year = policy.currentYear();
        Map<Long, Counts> cur = counts(year);
        int intake = 0, regular = 0, sup = 0;
        Set<Long> above = new HashSet<>();
        List<Course> all = courses.findAll();
        for (Course c : all) {
            Counts cc = cur.getOrDefault(c.id, new Counts());
            intake += c.approvedIntake;
            regular += cc.regular;
            sup += cc.supernumerary;
            if (cc.regular > c.approvedIntake) above.add(c.institutionId);
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("admissionYear", year);
        m.put("institutions", institutions.count());
        m.put("courses", all.size());
        m.put("approvedIntake", intake);
        m.put("regularEnrolled", regular);
        m.put("supernumeraryEnrolled", sup);
        m.put("fillPct", intake == 0 ? 0 : Math.round(regular * 1000.0 / intake) / 10.0);
        m.put("institutionsAboveIntake", above.size());
        m.put("openAlerts", alerts.findByAdmissionYearOrderByIdDesc(year).stream().filter(a -> "OPEN".equals(a.status)).count());
        m.put("openDuplicateCases", dups.findByStatus("OPEN").size());
        m.put("apaarIssues", records.countByAdmissionYearAndApaarStatusNot(year, Const.APAAR_VALID));
        Map<String, Long> conf = new LinkedHashMap<>();
        for (String s : List.of(Const.CONF_VERIFIED, Const.CONF_PENDING, Const.CONF_DISPUTED, Const.CONF_UNCONFIRMED,
                Const.CONF_REVIEWED, Const.CONF_NOT_REQUIRED)) {
            conf.put(s, records.countByAdmissionYearAndConfirmationStatus(year, s));
        }
        m.put("confirmation", conf);
        return m;
    }

    public List<Map<String, Object>> institutionTable() {
        int year = policy.currentYear();
        Map<Long, Counts> cur = counts(year);
        List<Course> all = courses.findAll();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Institution in : institutions.findAll()) {
            int intake = 0, regular = 0, sup = 0, over = 0, nCourses = 0;
            for (Course c : all) {
                if (!c.institutionId.equals(in.id)) continue;
                nCourses++;
                Counts cc = cur.getOrDefault(c.id, new Counts());
                intake += c.approvedIntake;
                regular += cc.regular;
                sup += cc.supernumerary;
                if (cc.regular > c.approvedIntake) over++;
            }
            long total = records.countByInstitutionIdAndAdmissionYear(in.id, year);
            long bad = records.countByInstitutionIdAndAdmissionYearAndApaarStatusNot(in.id, year, Const.APAAR_VALID);
            long ver = records.countByInstitutionIdAndAdmissionYearAndConfirmationStatus(in.id, year, Const.CONF_VERIFIED);
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("id", in.id);
            r.put("aicteId", in.aicteId);
            r.put("name", in.name);
            r.put("state", in.state);
            r.put("courses", nCourses);
            r.put("approvedIntake", intake);
            r.put("regularEnrolled", regular);
            r.put("supernumeraryEnrolled", sup);
            r.put("fillPct", intake == 0 ? 0 : Math.round(regular * 1000.0 / intake) / 10.0);
            r.put("coursesOverIntake", over);
            r.put("records", total);
            r.put("apaarIssues", bad);
            r.put("studentVerified", ver);
            r.put("hasUpload", total > 0);
            rows.add(r);
        }
        return rows;
    }

    public List<Map<String, Object>> alertViews(Long institutionIdOrNull) {
        int year = policy.currentYear();
        Map<Long, Institution> inst = institutions.findAll().stream().collect(Collectors.toMap(i -> i.id, i -> i));
        Map<Long, Course> crs = courses.findAll().stream().collect(Collectors.toMap(c -> c.id, c -> c));
        List<Alert> list = institutionIdOrNull == null
                ? alerts.findByAdmissionYearOrderByIdDesc(year)
                : alerts.findByInstitutionIdAndAdmissionYearOrderByIdDesc(institutionIdOrNull, year);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Alert a : list) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", a.id);
            m.put("type", a.type);
            m.put("severity", a.severity);
            m.put("status", a.status);
            m.put("institution", inst.containsKey(a.institutionId) ? inst.get(a.institutionId).name : "");
            m.put("institutionId", a.institutionId);
            m.put("course", crs.containsKey(a.courseId) ? crs.get(a.courseId).name : "");
            m.put("currentValue", a.currentValue);
            m.put("baselineValue", a.baselineValue);
            m.put("explanation", a.message);
            m.put("note", a.note);
            m.put("updatedAt", a.updatedAt);
            out.add(m);
        }
        return out;
    }

    public List<Map<String, Object>> watchlist() {
        int year = policy.currentYear();
        List<Alert> live = alerts.findByAdmissionYearOrderByIdDesc(year).stream()
                .filter(a -> "OPEN".equals(a.status) || "REVIEWED".equals(a.status)).toList();
        List<DuplicateCase> openDups = dups.findByStatus("OPEN");
        List<Map<String, Object>> out = new ArrayList<>();
        for (Institution in : institutions.findAll()) {
            int score = 0;
            List<String> reasons = new ArrayList<>();
            int intakeScore = 0;
            for (Alert a : live) {
                if (!a.institutionId.equals(in.id)) continue;
                switch (a.type) {
                    case "INTAKE_EXCEEDED" -> { intakeScore += 35; reasons.add(a.message); }
                    case "SUPERNUMERARY_EXCEEDED" -> { score += 15; reasons.add(a.message); }
                    case "ENROLMENT_SPIKE" -> { score += 20; reasons.add(a.message); }
                    default -> { }
                }
            }
            score += Math.min(intakeScore, 70);
            long nDup = openDups.stream()
                    .filter(d -> in.id.equals(d.institutionAId) || in.id.equals(d.institutionBId)).count();
            if (nDup > 0) {
                score += (int) Math.min(30, nDup * 10);
                reasons.add(nDup + " student(s) appear to be enrolled here and at another institution at the same time.");
            }
            long total = records.countByInstitutionIdAndAdmissionYear(in.id, year);
            if (total >= 20) {
                long bad = records.countByInstitutionIdAndAdmissionYearAndApaarStatusNot(in.id, year, Const.APAAR_VALID);
                double badPct = bad * 100.0 / total;
                if (badPct >= 5) {
                    score += 20;
                    reasons.add(String.format(Locale.ROOT, "%.1f%% of this year's records (%d of %d) have missing or unmatched APAAR IDs.", badPct, bad, total));
                }
                long dis = records.countByInstitutionIdAndAdmissionYearAndConfirmationStatus(in.id, year, Const.CONF_DISPUTED);
                double disPct = dis * 100.0 / total;
                if (disPct >= 5) {
                    score += 15;
                    reasons.add(String.format(Locale.ROOT, "%.1f%% of records (%d) were disputed by the students themselves.", disPct, dis));
                }
                long ver = records.countByInstitutionIdAndAdmissionYearAndConfirmationStatus(in.id, year, Const.CONF_VERIFIED);
                double verPct = ver * 100.0 / total;
                if (verPct < 20 && total >= 30 && !"OFF".equals(policy.confirmationMode())) {
                    score += 10;
                    reasons.add(String.format(Locale.ROOT, "Only %.1f%% of students have confirmed their enrolment.", verPct));
                }
            }
            score = Math.min(100, score);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("institutionId", in.id);
            m.put("aicteId", in.aicteId);
            m.put("name", in.name);
            m.put("state", in.state);
            m.put("score", score);
            m.put("level", score >= 60 ? "HIGH" : score >= 30 ? "MEDIUM" : "LOW");
            m.put("reasons", reasons);
            out.add(m);
        }
        out.sort((a, b) -> Integer.compare((Integer) b.get("score"), (Integer) a.get("score")));
        return out;
    }

    // ---------------------------------------------------------------- reports

    public String reportCsv(String type) {
        StringBuilder sb = new StringBuilder();
        int year = policy.currentYear();
        switch (type) {
            case "institution-enrolment" -> {
                sb.append("AICTE ID,Institution,State,Approved intake,Regular enrolled,Supernumerary enrolled,Fill %,Courses above intake\n");
                for (Map<String, Object> r : institutionTable()) {
                    sb.append(csv(r.get("aicteId"))).append(',').append(csv(r.get("name"))).append(',')
                            .append(csv(r.get("state"))).append(',').append(r.get("approvedIntake")).append(',')
                            .append(r.get("regularEnrolled")).append(',').append(r.get("supernumeraryEnrolled")).append(',')
                            .append(r.get("fillPct")).append(',').append(r.get("coursesOverIntake")).append('\n');
                }
            }
            case "category-distribution" -> {
                sb.append("Admission year,Category,Students\n");
                for (Object[] r : records.countsByCategory(year)) sb.append(year).append(',').append(csv(r[0])).append(',').append(r[1]).append('\n');
            }
            case "gender-distribution" -> {
                sb.append("Admission year,Gender,Students\n");
                for (Object[] r : records.countsByGender(year)) sb.append(year).append(',').append(csv(r[0])).append(',').append(r[1]).append('\n');
            }
            case "alerts" -> {
                sb.append("Type,Severity,Status,Institution,Course,Explanation\n");
                for (Map<String, Object> a : alertViews(null)) {
                    sb.append(csv(a.get("type"))).append(',').append(csv(a.get("severity"))).append(',').append(csv(a.get("status")))
                            .append(',').append(csv(a.get("institution"))).append(',').append(csv(a.get("course")))
                            .append(',').append(csv(a.get("explanation"))).append('\n');
                }
            }
            case "watchlist" -> {
                sb.append("AICTE ID,Institution,State,Risk score,Level,Reasons\n");
                for (Map<String, Object> w : watchlist()) {
                    sb.append(csv(w.get("aicteId"))).append(',').append(csv(w.get("name"))).append(',').append(csv(w.get("state")))
                            .append(',').append(w.get("score")).append(',').append(csv(w.get("level"))).append(',')
                            .append(csv(String.join(" | ", (List<String>) w.get("reasons")))).append('\n');
                }
            }
            default -> throw new IllegalArgumentException("Unknown report: " + type);
        }
        return sb.toString();
    }

    private static String csv(Object o) {
        String s = o == null ? "" : String.valueOf(o);
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) return "\"" + s.replace("\"", "\"\"") + "\"";
        return s;
    }
}
