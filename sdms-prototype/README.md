# EduTrace (prototype)

EduTrace is a hackathon prototype for: *APAAR ID-Based Student Data Management and Verification Platform for AICTE-Approved Institutions*.

React (frontend) + Spring Boot (backend). All data is synthetic. This is not an official Government of India website.

## Status (please read)

- **Frontend**: built successfully (`npm run build`) and checked in a browser against a mock API.
- **Backend**: written but **never compiled or run** by me, because Maven Central was blocked in my workspace. Only a syntax-level parse check was done. Run `mvn spring-boot:run` and fix any compile or runtime errors that appear; they are likely small.

## Run

Requirements: Java 21, Maven, Node 18+.

```
cd backend
mvn spring-boot:run          # http://localhost:8080  (H2 file database, seeded on first start)

cd frontend
npm install
npm run dev                  # http://localhost:5173  (proxies /api to :8080)
```

MySQL instead of H2: create a database `sdms`, then `mvn spring-boot:run -Dspring-boot.run.profiles=mysql` (edit `application-mysql.properties` for credentials).

## Demo accounts

| Role | Login | Password |
|---|---|---|
| AICTE Admin | admin | Admin@123 |
| Institution | inst1001 … inst1008 | Inst@123 |
| Verified employer | employer1 | Emp@12345 |
| Student | APAAR ID 100000000001 – 100000000005 (tap a demo ID on the page) | The OTP is shown on the page |

There is no email and no outbox. One-time codes are shown on the sign-in page (demo mode), and access requests appear in the student's own profile under "Access requests".

## Suggested walk-through

1. Log in as `inst1008`. Upload `sample-data/inst1008_upload_with_errors.csv`: the dry-run shows every problem before anything is saved.
2. Upload `sample-data/inst1008_upload_clean.csv` and commit it.
3. Log in as `admin`: dashboard, alerts (intake exceeded, enrolment spike, supernumerary), duplicate cases, watchlist, audit log.
4. Log in as a student (`100000000001`), use the on-screen demo code, confirm or dispute the record, view the certificate with QR.
5. Log in as `employer1`, request access to student `100000000001`. The student gets an email, approves choosing fields and duration, and the employer then sees only those fields.
6. Open the QR/verify link to see the signed public verification.

## Design notes

- Data minimisation: no Aadhaar, marks, address or phone are stored.
- Validation engine is shared by dry-run, real upload and JSON bulk API.
- Audit log is hash-chained and verifiable.
- Verification payloads are HMAC-SHA256 signed.
- Layout follows GIGW 3.0 / UX4G ideas: skip link, text size, high contrast, English/Hindi labels, real tables, text badges.

## Prototype limitations

- APAAR registry is a mock seeded table, not the real ABC service.
- OTPs are kept in memory; JWT is stored in localStorage; secrets in `application.properties` are for demo only.
- No real emblem or AICTE logo is used.
