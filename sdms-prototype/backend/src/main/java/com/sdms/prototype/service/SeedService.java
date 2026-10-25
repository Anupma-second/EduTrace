package com.sdms.prototype.service;

import com.sdms.prototype.domain.*;
import com.sdms.prototype.domain.Repos.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Creates SYNTHETIC demo data on first start: fictional institutions, fictional students,
 * a mock APAAR registry and planted anomalies for the demo. No real person or institution is represented.
 */
@Component
public class SeedService implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(SeedService.class);

    private record CourseDef(String code, String name, String level, int intake, int sup, int years) {}
    private record InstDef(String aicteId, String name, String state, String district, List<CourseDef> courses) {}

    private static final String[] FIRST = {"Aarav", "Vivaan", "Aditya", "Arjun", "Rohan", "Karan", "Rahul", "Siddharth", "Ishaan", "Kabir",
            "Ananya", "Diya", "Isha", "Kavya", "Meera", "Neha", "Pooja", "Riya", "Sneha", "Tanvi",
            "Imran", "Farhan", "Zoya", "Sana", "Gurpreet", "Harleen", "Joseph", "Maria", "Thomas", "Anjali",
            "Manoj", "Suresh", "Deepak", "Lakshmi", "Priyanka", "Nikhil", "Varun", "Shreya", "Pranav", "Aditi"};
    private static final String[] LAST = {"Sharma", "Verma", "Kumar", "Singh", "Gupta", "Patel", "Reddy", "Nair", "Iyer", "Das",
            "Mishra", "Yadav", "Pandey", "Joshi", "Mehta", "Shah", "Khan", "Ansari", "Chopra", "Kapoor",
            "Rao", "Menon", "Pillai", "Bose", "Sen", "Ghosh", "Banerjee", "Chauhan", "Thakur", "Tiwari",
            "Agarwal", "Jain", "Malhotra", "Bhatt", "Desai", "Kulkarni", "Naidu", "Sethi", "Mahapatra", "Mohanty"};

    private final InstitutionRepo institutions;
    private final CourseRepo courses;
    private final ApaarRepo registry;
    private final RecordRepo records;
    private final DupRepo dups;
    private final UserRepo users;
    private final RequesterRepo requesters;
    private final PolicyService policy;
    private final MonitoringService monitoring;
    private final AuditService audit;
    private final PasswordEncoder encoder;
    private final PlatformTransactionManager tm;

    private final Set<String> usedIds = new HashSet<>();
    private Random rnd;

    public SeedService(InstitutionRepo institutions, CourseRepo courses, ApaarRepo registry, RecordRepo records,
                       DupRepo dups, UserRepo users, RequesterRepo requesters, PolicyService policy,
                       MonitoringService monitoring, AuditService audit, PasswordEncoder encoder,
                       PlatformTransactionManager tm) {
        this.institutions = institutions;
        this.courses = courses;
        this.registry = registry;
        this.records = records;
        this.dups = dups;
        this.users = users;
        this.requesters = requesters;
        this.policy = policy;
        this.monitoring = monitoring;
        this.audit = audit;
        this.encoder = encoder;
        this.tm = tm;
    }

    @Override
    public void run(String... args) {
        if (institutions.count() > 0) return;
        log.info("Empty database: loading synthetic demo data ...");
        new TransactionTemplate(tm).executeWithoutResult(s -> seed());
        log.info("Demo data ready.");
    }

    private void seed() {
        rnd = new Random(42);
        int cur = policy.currentYear();
        policy.set(PolicyService.INTAKE_TOLERANCE, "0");
        policy.set(PolicyService.SPIKE_THRESHOLD, "30");
        policy.set(PolicyService.CONFIRMATION_MODE, "OPTIONAL");
        policy.set(PolicyService.CONFIRMATION_DAYS, "30");

        List<InstDef> defs = List.of(
                new InstDef("AICTE-1001", "Magadh Institute of Engineering and Technology", "Bihar", "Patna", List.of(
                        new CourseDef("BTECH-CSE", "B.Tech Computer Science", "UG", 120, 12, 4),
                        new CourseDef("BTECH-ME", "B.Tech Mechanical Engineering", "UG", 60, 6, 4),
                        new CourseDef("MCA", "Master of Computer Applications", "PG", 60, 6, 2))),
                new InstDef("AICTE-1002", "Deccan Valley College of Engineering", "Maharashtra", "Pune", List.of(
                        new CourseDef("BTECH-CSE", "B.Tech Computer Science", "UG", 120, 12, 4),
                        new CourseDef("BTECH-ECE", "B.Tech Electronics and Communication", "UG", 90, 9, 4),
                        new CourseDef("BTECH-CE", "B.Tech Civil Engineering", "UG", 60, 6, 4))),
                new InstDef("AICTE-1003", "Kaveri Institute of Management Studies", "Karnataka", "Bengaluru", List.of(
                        new CourseDef("MBA", "Master of Business Administration", "PG", 120, 12, 2),
                        new CourseDef("PGDM", "Post Graduate Diploma in Management", "PG", 60, 6, 2))),
                new InstDef("AICTE-1004", "Aravalli Institute of Pharmaceutical Sciences", "Rajasthan", "Jaipur", List.of(
                        new CourseDef("BPHARM", "Bachelor of Pharmacy", "UG", 60, 6, 4),
                        new CourseDef("MPHARM", "Master of Pharmacy", "PG", 30, 3, 2))),
                new InstDef("AICTE-1005", "Coromandel College of Engineering", "Tamil Nadu", "Chennai", List.of(
                        new CourseDef("BTECH-ME", "B.Tech Mechanical Engineering", "UG", 120, 12, 4),
                        new CourseDef("BTECH-EEE", "B.Tech Electrical and Electronics", "UG", 90, 9, 4))),
                new InstDef("AICTE-1006", "Gomti Institute of Technology and Management", "Uttar Pradesh", "Lucknow", List.of(
                        new CourseDef("BTECH-CSE", "B.Tech Computer Science", "UG", 120, 12, 4),
                        new CourseDef("BARCH", "Bachelor of Architecture", "UG", 40, 4, 5))),
                new InstDef("AICTE-1007", "Golconda School of Architecture and Planning", "Telangana", "Hyderabad", List.of(
                        new CourseDef("BARCH", "Bachelor of Architecture", "UG", 60, 6, 5),
                        new CourseDef("MARCH", "Master of Architecture", "PG", 20, 2, 2))),
                new InstDef("AICTE-1008", "Mahanadi Institute of Technology and Management", "Odisha", "Bhubaneswar", List.of(
                        new CourseDef("BTECH-CSE", "B.Tech Computer Science", "UG", 30, 3, 4),
                        new CourseDef("BTECH-CE", "B.Tech Civil Engineering", "UG", 20, 2, 4))));

        Map<String, Long> instIds = new LinkedHashMap<>();
        Map<String, Course> courseMap = new LinkedHashMap<>(); // "AICTE-1001|BTECH-CSE" -> course
        for (InstDef d : defs) {
            Institution in = new Institution();
            in.aicteId = d.aicteId();
            in.name = d.name();
            in.state = d.state();
            in.district = d.district();
            institutions.save(in);
            instIds.put(d.aicteId(), in.id);
            for (CourseDef cd : d.courses()) {
                Course c = new Course();
                c.institutionId = in.id;
                c.code = cd.code();
                c.name = cd.name();
                c.level = cd.level();
                c.approvedIntake = cd.intake();
                c.supernumeraryLimit = cd.sup();
                c.durationYears = cd.years();
                courses.save(c);
                courseMap.put(d.aicteId() + "|" + cd.code(), c);
            }
        }

        // fixed demo students (enrolled at institution 1, B.Tech CSE, current year)
        String[][] demo = {
                {"100000000001", "Aarav Sharma", "MALE", "2008-03-14", "GEN"},
                {"100000000002", "Ananya Verma", "FEMALE", "2008-07-22", "OBC"},
                {"100000000003", "Rohan Kumar", "MALE", "2007-11-02", "SC"},
                {"100000000004", "Priya Singh", "FEMALE", "2008-01-30", "GEN"},
                {"100000000005", "Imran Ansari", "MALE", "2008-09-09", "OBC"}};
        usedIds.addAll(List.of("100000000001", "100000000002", "100000000003", "100000000004", "100000000005",
                "100000000101", "100000000102", "100000000103"));

        List<EnrolmentRecord> batch = new ArrayList<>();
        List<ApaarPerson> persons = new ArrayList<>();

        for (InstDef d : defs) {
            Long instId = instIds.get(d.aicteId());
            for (CourseDef cd : d.courses()) {
                Course c = courseMap.get(d.aicteId() + "|" + cd.code());
                for (int year = cur - 3; year <= cur; year++) {
                    boolean isCur = year == cur;
                    int regTarget;
                    int supTarget;
                    if (isCur && d.aicteId().equals("AICTE-1008")) continue; // fresh institution with no upload yet
                    if (isCur) {
                        regTarget = (int) Math.round(cd.intake() * (0.80 + rnd.nextDouble() * 0.15));
                        supTarget = rnd.nextInt(cd.sup() + 1);
                    } else {
                        regTarget = (int) Math.round(cd.intake() * (0.90 + rnd.nextDouble() * 0.10));
                        supTarget = rnd.nextInt(cd.sup() + 1);
                    }
                    // planted anomalies
                    if (d.aicteId().equals("AICTE-1002") && cd.code().equals("BTECH-CSE") && isCur) regTarget = 132;
                    if (d.aicteId().equals("AICTE-1005") && cd.code().equals("BTECH-ME")) {
                        if (isCur) regTarget = 96;
                        else if (year == cur - 1) regTarget = 60;
                    }
                    if (d.aicteId().equals("AICTE-1006") && cd.code().equals("BARCH") && isCur) supTarget = 8;

                    int seq = 1;
                    int fixed = 0;
                    if (isCur && d.aicteId().equals("AICTE-1001") && cd.code().equals("BTECH-CSE")) {
                        for (String[] f : demo) {
                            String roll = year + "CSE" + String.format("%03d", seq++);
                            EnrolmentRecord r = rec(instId, c, year, f[1], LocalDate.parse(f[3]), f[2], f[4], "REGULAR", "INDIAN",
                                    "COUNSELLING", f[0], Const.APAAR_VALID, Const.CONF_PENDING, null, roll);
                            batch.add(r);
                            persons.add(person(f[0], f[1], LocalDate.parse(f[3]), f[1].toLowerCase().replace(' ', '.') + "@student.edutrace.in"));
                            fixed++;
                        }
                    }
                    for (int i = 0; i < regTarget - fixed; i++) {
                        newStudent(batch, persons, d, instId, c, cd, year, isCur, false, seq++);
                    }
                    for (int i = 0; i < supTarget; i++) {
                        newStudent(batch, persons, d, instId, c, cd, year, isCur, true, seq++);
                    }
                    if (batch.size() >= 500) flush(batch, persons);
                }
            }
        }

        // students enrolled at two institutions at once (planted cross-institution duplicates)
        String[][] dupStudents = {
                {"100000000101", "Neha Gupta", "FEMALE", "2008-02-11"},
                {"100000000102", "Vikram Rao", "MALE", "2007-12-05"},
                {"100000000103", "Sana Khan", "FEMALE", "2008-06-19"}};
        Long i1 = instIds.get("AICTE-1001"), i2 = instIds.get("AICTE-1002");
        Course c1 = courseMap.get("AICTE-1001|BTECH-CSE"), c2 = courseMap.get("AICTE-1002|BTECH-ECE");
        List<EnrolmentRecord> dupA = new ArrayList<>();
        List<EnrolmentRecord> dupB = new ArrayList<>();
        for (String[] s : dupStudents) {
            LocalDate dob = LocalDate.parse(s[3]);
            persons.add(person(s[0], s[1], dob, s[1].toLowerCase().replace(' ', '.') + "@student.edutrace.in"));
            EnrolmentRecord a = rec(i1, c1, cur, s[1], dob, s[2], "GEN", "REGULAR", "INDIAN", "COUNSELLING", s[0],
                    Const.APAAR_VALID, Const.CONF_PENDING, "DUPLICATE_ENROLMENT", cur + "CSE9" + s[0].substring(10));
            EnrolmentRecord b = rec(i2, c2, cur, s[1], dob, s[2], "GEN", "REGULAR", "INDIAN", "MANAGEMENT", s[0],
                    Const.APAAR_VALID, Const.CONF_PENDING, "DUPLICATE_ENROLMENT", cur + "ECE9" + s[0].substring(10));
            dupA.add(a);
            dupB.add(b);
            batch.add(a);
            batch.add(b);
        }
        flush(batch, persons);

        // spare registry persons (not enrolled anywhere) used by the sample upload files
        try (InputStream in = new ClassPathResource("seed/spare-persons.csv").getInputStream()) {
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            for (Map<String, String> m : CsvUtil.toMaps(CsvUtil.parse(text))) {
                usedIds.add(m.get("apaar_id"));
                registry.save(person(m.get("apaar_id"), m.get("name"), LocalDate.parse(m.get("dob")), m.get("email")));
            }
        } catch (Exception e) {
            log.warn("Could not load spare persons: {}", e.getMessage());
        }

        for (int k = 0; k < dupA.size(); k++) {
            DuplicateCase dc = new DuplicateCase();
            dc.apaarId = dupA.get(k).apaarId;
            dc.recordAId = dupA.get(k).id;
            dc.recordBId = dupB.get(k).id;
            dc.institutionAId = i1;
            dc.institutionBId = i2;
            dc.status = "OPEN";
            dc.createdAt = Instant.now().minus(k == 0 ? 20 : 3, ChronoUnit.DAYS);
            dc.deadline = dc.createdAt.plus(14, ChronoUnit.DAYS);
            dups.save(dc);
        }

        // users
        mkUser("admin", "Admin@123", Const.ROLE_ADMIN, null, null, "AICTE Administrator", "admin@edutrace.in");
        for (InstDef d : defs) {
            String uname = "inst" + d.aicteId().substring(6);
            mkUser(uname, "Inst@123", Const.ROLE_INSTITUTION, instIds.get(d.aicteId()), null,
                    d.name() + " - Registrar", uname + "@edutrace.in");
        }
        Requester emp = new Requester();
        emp.fullName = "Demo HR Officer";
        emp.organisation = "Sample Employer Pvt Ltd";
        emp.orgRegId = "REG-DEMO-001";
        emp.email = "hr@employer.edutrace.in";
        emp.tier = Const.TIER_VERIFIED;
        emp.verifiedAt = Instant.now();
        emp.createdAt = Instant.now();
        requesters.save(emp);
        mkUser("employer1", "Emp@12345", Const.ROLE_REQUESTER, null, emp.id, emp.fullName, emp.email);

        monitoring.recomputeAlerts();
        audit.log("system", "SYSTEM", "SEED", "database", null, "Synthetic demo data loaded");
    }

    private void newStudent(List<EnrolmentRecord> batch, List<ApaarPerson> persons, InstDef d, Long instId, Course c,
                            CourseDef cd, int year, boolean isCur, boolean supernumerary, int seq) {
        String first = FIRST[rnd.nextInt(FIRST.length)];
        String last = LAST[rnd.nextInt(LAST.length)];
        String name = first + " " + last;
        int by = year - 18 - rnd.nextInt(3) - (cd.level().equals("PG") ? 4 : 0);
        LocalDate dob = LocalDate.of(by, 1 + rnd.nextInt(12), 1 + rnd.nextInt(28));
        double g = rnd.nextDouble();
        String gender = g < 0.62 ? "MALE" : g < 0.98 ? "FEMALE" : "OTHER";
        String category;
        String residency;
        String seat = supernumerary ? "SUPERNUMERARY" : "REGULAR";
        if (supernumerary) {
            category = "NA";
            double x = rnd.nextDouble();
            residency = x < 0.45 ? "FN" : x < 0.8 ? "OCI" : "NRI";
        } else {
            double x = rnd.nextDouble();
            category = x < 0.45 ? "GEN" : x < 0.72 ? "OBC" : x < 0.87 ? "SC" : x < 0.94 ? "ST" : "EWS";
            double y = rnd.nextDouble();
            residency = y < 0.97 ? "INDIAN" : y < 0.99 ? "NRI" : "OCI";
        }
        double m = rnd.nextDouble();
        String mode = supernumerary ? "DIRECT" : m < 0.70 ? "COUNSELLING" : m < 0.85 ? "MANAGEMENT" : m < 0.95 ? "DIRECT" : "LATERAL";

        String apaarId = newId();
        String apaarStatus = Const.APAAR_VALID;
        double badRate = isCur ? (d.aicteId().equals("AICTE-1006") ? 0.07 : 0.015) : 0.0;
        if (rnd.nextDouble() < badRate) {
            int k = rnd.nextInt(4);
            switch (k) {
                case 0 -> { apaarStatus = Const.APAAR_MISSING; apaarId = null; }
                case 1 -> { apaarStatus = Const.APAAR_INVALID_FORMAT; apaarId = apaarId.substring(0, 11); }
                case 2 -> apaarStatus = Const.APAAR_NOT_FOUND;
                default -> apaarStatus = Const.APAAR_MISMATCH;
            }
        }
        String email = (first + "." + last + (apaarId == null ? "x" : apaarId.substring(Math.max(0, apaarId.length() - 4)))).toLowerCase() + "@student.edutrace.in";
        if (apaarStatus.equals(Const.APAAR_VALID)) persons.add(person(apaarId, name, dob, email));
        else if (apaarStatus.equals(Const.APAAR_MISMATCH)) persons.add(person(apaarId, name, dob.plusDays(3), email));

        String conf;
        String note = null;
        if (!isCur) conf = Const.CONF_NOT_REQUIRED;
        else if (!apaarStatus.equals(Const.APAAR_VALID)) conf = Const.CONF_PENDING;
        else {
            double v = rnd.nextDouble();
            double verified = d.aicteId().equals("AICTE-1004") ? 0.08 : 0.55;
            double disputed = d.aicteId().equals("AICTE-1003") ? 0.09 : 0.03;
            if (v < verified) conf = Const.CONF_VERIFIED;
            else if (v < verified + disputed) {
                conf = Const.CONF_DISPUTED;
                String[] reasons = {"My name is spelt differently in this record", "I am not enrolled in this course",
                        "The date of birth shown is wrong", "I withdrew my admission before the session began"};
                note = reasons[rnd.nextInt(reasons.length)];
            } else conf = Const.CONF_PENDING;
        }
        String roll = year + cd.code().replaceAll("[^A-Z]", "").substring(0, Math.min(3, cd.code().replaceAll("[^A-Z]", "").length()))
                + String.format("%03d", seq);
        EnrolmentRecord r = rec(instId, c, year, name, dob, gender, category, seat, residency, mode, apaarId, apaarStatus, conf,
                apaarStatus.equals(Const.APAAR_VALID) ? null : "APAAR_" + apaarStatus, roll);
        r.confirmationNote = note;
        if (year + cd.years() <= policy.currentYear()) r.status = "GRADUATED";
        else if (rnd.nextDouble() < 0.02 && !isCur) r.status = "WITHDRAWN";
        batch.add(r);
    }

    private EnrolmentRecord rec(Long instId, Course c, int year, String name, LocalDate dob, String gender, String category,
                                String seat, String residency, String mode, String apaarId, String apaarStatus,
                                String conf, String flags, String roll) {
        EnrolmentRecord r = new EnrolmentRecord();
        r.apaarId = apaarId;
        r.rollNo = roll;
        r.name = name;
        r.dob = dob;
        r.gender = gender;
        r.institutionId = instId;
        r.courseId = c.id;
        r.admissionYear = year;
        r.category = category;
        r.seatType = seat;
        r.residency = residency;
        r.admissionMode = mode;
        r.status = "ACTIVE";
        r.apaarStatus = apaarStatus;
        r.confirmationStatus = conf;
        r.flags = flags;
        r.createdAt = Instant.now().minus(rnd.nextInt(20), ChronoUnit.DAYS);
        r.updatedAt = r.createdAt;
        return r;
    }

    private ApaarPerson person(String id, String name, LocalDate dob, String email) {
        ApaarPerson p = new ApaarPerson();
        p.apaarId = id;
        p.name = name;
        p.dob = dob;
        p.email = email;
        p.active = true;
        return p;
    }

    private void flush(List<EnrolmentRecord> batch, List<ApaarPerson> persons) {
        registry.saveAll(persons);
        records.saveAll(batch);
        batch.clear();
        persons.clear();
    }

    private String newId() {
        while (true) {
            StringBuilder sb = new StringBuilder();
            sb.append(3 + rnd.nextInt(7));
            for (int i = 0; i < 11; i++) sb.append(rnd.nextInt(10));
            String s = sb.toString();
            if (usedIds.add(s)) return s;
        }
    }

    private void mkUser(String username, String password, String role, Long instId, Long reqId, String fullName, String email) {
        AppUser u = new AppUser();
        u.username = username;
        u.passwordHash = encoder.encode(password);
        u.role = role;
        u.institutionId = instId;
        u.requesterId = reqId;
        u.fullName = fullName;
        u.email = email;
        u.otpVerified = true;
        users.save(u);
    }
}
