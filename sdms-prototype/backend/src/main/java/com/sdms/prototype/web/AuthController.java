package com.sdms.prototype.web;

import com.sdms.prototype.domain.*;
import com.sdms.prototype.domain.Repos.*;
import com.sdms.prototype.security.AuthUser;
import com.sdms.prototype.security.JwtService;
import com.sdms.prototype.service.AuditService;
import com.sdms.prototype.service.MailService;
import com.sdms.prototype.service.OtpService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final UserRepo users;
    private final RequesterRepo requesters;
    private final ApaarRepo registry;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final OtpService otp;
    private final MailService mail;
    private final AuditService audit;
    private final InstitutionRepo institutions;

    public AuthController(UserRepo users, RequesterRepo requesters, ApaarRepo registry, PasswordEncoder encoder,
                          JwtService jwt, OtpService otp, MailService mail, AuditService audit, InstitutionRepo institutions) {
        this.users = users;
        this.requesters = requesters;
        this.registry = registry;
        this.encoder = encoder;
        this.jwt = jwt;
        this.otp = otp;
        this.mail = mail;
        this.audit = audit;
        this.institutions = institutions;
    }

    private static String s(Map<String, Object> m, String k) {
        Object v = m.get(k);
        return v == null ? "" : String.valueOf(v).trim();
    }

    private Map<String, Object> session(AuthUser u) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("token", jwt.issue(u));
        out.putAll(profile(u));
        return out;
    }

    private Map<String, Object> profile(AuthUser u) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("username", u.username());
        out.put("role", u.role());
        out.put("name", u.displayName());
        if (u.institutionId() != null) {
            out.put("institutionId", u.institutionId());
            institutions.findById(u.institutionId()).ifPresent(i -> {
                out.put("institutionName", i.name);
                out.put("aicteId", i.aicteId);
            });
        }
        if (u.requesterId() != null) {
            requesters.findById(u.requesterId()).ifPresent(r -> {
                out.put("tier", r.tier);
                out.put("organisation", r.organisation);
            });
        }
        if (u.apaarId() != null) out.put("apaarId", u.apaarId());
        return out;
    }

    /** Staff login (AICTE admin, institution) and requester login. */
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, Object> body) {
        String username = s(body, "username").toLowerCase();
        String password = s(body, "password");
        AppUser u = users.findByUsername(username).orElse(null);
        if (u == null || !encoder.matches(password, u.passwordHash)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid username or password."));
        }
        if (!u.otpVerified) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Verify your email address first (enter the code we sent you)."));
        }
        AuthUser au = new AuthUser(u.username, u.role, u.institutionId, u.requesterId, null, u.fullName);
        audit.log(u.username, u.role, "LOGIN", "session", null, null);
        return ResponseEntity.ok(session(au));
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(@AuthenticationPrincipal AuthUser me) {
        if (me == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        return ResponseEntity.ok(profile(me));
    }

    // ---------------------------------------------------------------- students: APAAR ID + email OTP

    @PostMapping("/student/request-otp")
    public ResponseEntity<?> studentOtp(@RequestBody Map<String, Object> body) {
        String apaar = s(body, "apaarId");
        if (!apaar.matches("\\d{12}")) {
            return ResponseEntity.badRequest().body(Map.of("error", "Enter the 12-digit APAAR ID (digits only)."));
        }
        ApaarPerson p = registry.findById(apaar).orElse(null);
        if (p == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "This APAAR ID is not registered. Check the number and try again."));
        }
        otp.issue("student:" + apaar);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("message", "A one-time code has been sent.");
        out.put("name", p.name);
        return ResponseEntity.ok(out);
    }

    @PostMapping("/student/verify-otp")
    public ResponseEntity<?> studentVerify(@RequestBody Map<String, Object> body) {
        String apaar = s(body, "apaarId");
        if (!apaar.matches("\\d{12}") || !otp.verify("student:" + apaar, s(body, "otp"))) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "The code is incorrect or has expired."));
        }
        ApaarPerson p = registry.findById(apaar).orElseThrow();
        AuthUser au = new AuthUser(apaar, Const.ROLE_STUDENT, null, null, apaar, p.name);
        audit.log(apaar, Const.ROLE_STUDENT, "LOGIN", "session", apaar, null);
        return ResponseEntity.ok(session(au));
    }

    // ---------------------------------------------------------------- requesters (employers / individuals)

    @PostMapping("/requester/register")
    public ResponseEntity<?> register(@RequestBody Map<String, Object> body) {
        String name = s(body, "fullName");
        String email = s(body, "email").toLowerCase();
        String password = s(body, "password");
        if (name.isEmpty() || !email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            return ResponseEntity.badRequest().body(Map.of("error", "Enter your name and a valid email address."));
        }
        if (password.length() < 8) return ResponseEntity.badRequest().body(Map.of("error", "Password must be at least 8 characters."));
        if (users.findByUsername(email).isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("error", "An account with this email already exists."));
        }
        Requester r = new Requester();
        r.fullName = name;
        r.organisation = s(body, "organisation");
        r.orgRegId = s(body, "orgRegId");
        r.email = email;
        r.tier = Const.TIER_GENERAL; // becomes VERIFIED only after an AICTE administrator checks the organisation
        r.createdAt = Instant.now();
        requesters.save(r);
        AppUser u = new AppUser();
        u.username = email;
        u.passwordHash = encoder.encode(password);
        u.role = Const.ROLE_REQUESTER;
        u.requesterId = r.id;
        u.fullName = name;
        u.email = email;
        u.otpVerified = false;
        users.save(u);
        String code = otp.issue("req:" + email);
        audit.log(email, Const.ROLE_REQUESTER, "REQUESTER_REGISTERED", "requester#" + r.id, null, "tier=GENERAL, pending email verification");
        return ResponseEntity.ok(Map.of("message", "Account created. Enter the verification code to activate it."));
    }

    @PostMapping("/requester/verify")
    public ResponseEntity<?> verify(@RequestBody Map<String, Object> body) {
        String email = s(body, "email").toLowerCase();
        AppUser u = users.findByUsername(email).orElse(null);
        if (u == null || !Const.ROLE_REQUESTER.equals(u.role) || !otp.verify("req:" + email, s(body, "otp"))) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "The code is incorrect or has expired."));
        }
        u.otpVerified = true;
        users.save(u);
        return ResponseEntity.ok(Map.of("message", "Email verified. You can now sign in."));
    }
}
