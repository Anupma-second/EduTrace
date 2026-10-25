# EduTrace

**APAAR ID-based student data management and verification platform for AICTE-approved institutions.**

Institutions upload student records, the regulator monitors them for anomalies, students approve who can see their data, and employers verify certificates through a signed QR link.

> Not an official Government of India website.

---

## Features

- **Institutions**: upload student data by CSV with a dry-run that lists every problem before anything is saved; manage students and certificates.
- **AICTE administrator**: dashboard, alerts (intake exceeded, enrolment spike, supernumerary seats), duplicate-enrolment cases, watchlist, student-wise records, policy rules and a tamper-evident audit log.
- **Students**: sign in with APAAR ID and OTP, confirm or dispute the record, approve or reject access requests with chosen fields and duration, download a certificate with QR.
- **Employers and individuals**: request access to a student's record and see only the fields the student approved.
- **Public verification**: open the certificate link or upload an image of the certificate to check it against the signed record.
- **Accessibility**: skip link, text size controls, high-contrast mode, English/Hindi labels.

## Tech stack

| Layer | Technology |
|---|---|
| Frontend | React 18, Vite, react-router-dom 6 |
| Backend | Spring Boot 3.3, Java 21, Spring Data JPA, JWT |
| Database | H2 (default, file based) or MySQL |
| Verification | HMAC-SHA256 signed links, QR codes (`jsqr` for reading) |

## Project structure

```
sdms-prototype/
├── backend/        Spring Boot API
├── frontend/       React app
├── sample-data/    CSV files for upload testing
└── README.md
```

## Getting started

**Requirements:** Java 21, Maven, Node 18 or newer.

### 1. Backend

```bash
cd backend
mvn spring-boot:run
```

Runs at `http://localhost:8080`. The H2 database is created on first start and seeded automatically.

### 2. Frontend

```bash
cd frontend
npm install
npm run dev
```

Runs at `http://localhost:5173` and proxies `/api` to the backend.

### Using MySQL instead of H2

Create an empty database, then start the backend with the `mysql` profile.

```sql
CREATE DATABASE sdms CHARACTER SET utf8mb4;
```

```powershell
$env:DB_HOST="localhost"
$env:DB_PORT="3306"
$env:DB_NAME="sdms"
$env:DB_USER="root"
$env:DB_PASSWORD="your-password"
mvn spring-boot:run "-Dspring-boot.run.profiles=mysql"
```

Tables are created automatically on first start.

## Sample logins

| Role | Login | Password |
|---|---|---|
| AICTE administrator | `admin` | `Admin@123` |
| Institution | `inst1001` to `inst1008` | `Inst@123` |
| Employer | `employer1` | `Emp@12345` |
| Student | APAAR ID `100000000001` to `100000000005` | OTP `482915` |

`inst1008` has no uploaded students, so it is the best account for trying the upload flow.

## Quick tour

1. Sign in as `inst1008` and upload `sample-data/inst1008_upload_with_errors.csv`. The dry-run lists every problem.
2. Upload `sample-data/inst1008_upload_clean.csv` and commit it.
3. Sign in as `admin` and look at the dashboard, alerts, duplicate cases, students, watchlist and audit log.
4. Sign in as student `100000000001`, confirm the record and open the certificate.
5. Sign in as `employer1` and request access to that student. The request appears in the student's profile, where they approve it and choose the fields.
6. Open the certificate's verification link, or upload an image of the certificate on the Verify page.

## Design notes

- **Data minimisation**: no Aadhaar number, marks, address or phone number is stored.
- **One validation engine** serves the dry-run, the real upload and the JSON bulk API.
- **Audit log** is hash-chained and can be verified for tampering.
- **No email is sent.** Access requests appear in the student's own profile.

## Limitations

- The APAAR registry is a seeded table, not the live national service.
- OTPs are held in memory and a fixed code is used for sign-in.
- JWT is kept in `localStorage`.
- The secrets in `application.properties` are placeholders. Replace them, and the fixed OTP, before any real deployment.
- No official emblem or AICTE logo is used.

## Licence

Add a licence here if you plan to share the code publicly.