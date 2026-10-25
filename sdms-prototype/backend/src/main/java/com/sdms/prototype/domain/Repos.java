package com.sdms.prototype.domain;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** All Spring Data repositories, grouped in one file to keep the prototype compact. */
public final class Repos {
    private Repos() {}

    public interface InstitutionRepo extends JpaRepository<Institution, Long> {
        Optional<Institution> findByAicteId(String aicteId);
    }

    public interface CourseRepo extends JpaRepository<Course, Long> {
        List<Course> findByInstitutionId(Long institutionId);
    }

    public interface ApaarRepo extends JpaRepository<ApaarPerson, String> {
    }

    public interface RecordRepo extends JpaRepository<EnrolmentRecord, Long> {
        List<EnrolmentRecord> findByInstitutionId(Long institutionId);

        List<EnrolmentRecord> findByApaarId(String apaarId);

        List<EnrolmentRecord> findByInstitutionIdAndAdmissionYear(Long institutionId, int admissionYear);

        List<EnrolmentRecord> findByConfirmationStatus(String confirmationStatus);

        List<EnrolmentRecord> findByAdmissionYear(int admissionYear);

        long countByAdmissionYearAndApaarStatusNot(int admissionYear, String apaarStatus);

        long countByAdmissionYearAndConfirmationStatus(int admissionYear, String confirmationStatus);

        long countByInstitutionIdAndAdmissionYear(Long institutionId, int admissionYear);

        long countByInstitutionIdAndAdmissionYearAndApaarStatusNot(Long institutionId, int admissionYear, String apaarStatus);

        long countByInstitutionIdAndAdmissionYearAndConfirmationStatus(Long institutionId, int admissionYear, String confirmationStatus);

        @Query("select r.courseId, r.seatType, r.residency, count(r) from EnrolmentRecord r "
                + "where r.admissionYear = :year and r.status <> 'WITHDRAWN' "
                + "group by r.courseId, r.seatType, r.residency")
        List<Object[]> countsByCourse(@Param("year") int year);

        @Query("select r.category, count(r) from EnrolmentRecord r "
                + "where r.admissionYear = :year and r.status <> 'WITHDRAWN' group by r.category")
        List<Object[]> countsByCategory(@Param("year") int year);

        @Query("select r.gender, count(r) from EnrolmentRecord r "
                + "where r.admissionYear = :year and r.status <> 'WITHDRAWN' group by r.gender")
        List<Object[]> countsByGender(@Param("year") int year);

        @Query("select r from EnrolmentRecord r where r.apaarId = :apaar and r.institutionId <> :inst and r.status = 'ACTIVE'")
        List<EnrolmentRecord> activeElsewhere(@Param("apaar") String apaar, @Param("inst") Long inst);
    }

    public interface IssueRepo extends JpaRepository<ValidationIssue, Long> {
        List<ValidationIssue> findTop300ByInstitutionIdOrderByIdDesc(Long institutionId);
    }

    public interface BatchRepo extends JpaRepository<UploadBatch, Long> {
        List<UploadBatch> findTop10ByInstitutionIdOrderByIdDesc(Long institutionId);
    }

    public interface AlertRepo extends JpaRepository<Alert, Long> {
        List<Alert> findByAdmissionYearOrderByIdDesc(int admissionYear);

        List<Alert> findByCourseIdAndTypeAndAdmissionYear(Long courseId, String type, int admissionYear);

        List<Alert> findByInstitutionIdAndAdmissionYearOrderByIdDesc(Long institutionId, int admissionYear);
    }

    public interface DupRepo extends JpaRepository<DuplicateCase, Long> {
        List<DuplicateCase> findAllByOrderByIdDesc();

        List<DuplicateCase> findByApaarIdAndStatus(String apaarId, String status);

        List<DuplicateCase> findByStatus(String status);

        List<DuplicateCase> findByInstitutionAIdOrInstitutionBIdOrderByIdDesc(Long a, Long b);
    }

    public interface RequesterRepo extends JpaRepository<Requester, Long> {
    }

    public interface AccessRepo extends JpaRepository<AccessRequest, Long> {
        List<AccessRequest> findByRequesterIdOrderByIdDesc(Long requesterId);

        List<AccessRequest> findByApaarIdOrderByIdDesc(String apaarId);

        long countByRequesterIdAndCreatedAtAfter(Long requesterId, Instant after);

        List<AccessRequest> findByStatus(String status);
    }

    public interface BlockRepo extends JpaRepository<BlockedRequester, Long> {
        boolean existsByApaarIdAndRequesterId(String apaarId, Long requesterId);

        List<BlockedRequester> findByApaarId(String apaarId);
    }

    public interface HistoryRepo extends JpaRepository<RecordHistory, Long> {
        List<RecordHistory> findByRecordIdOrderByIdDesc(Long recordId);
    }

    public interface AuditRepo extends JpaRepository<AuditEntry, Long> {
        Optional<AuditEntry> findTopByOrderByIdDesc();

        List<AuditEntry> findTop200ByOrderByIdDesc();

        List<AuditEntry> findTop100BySubjectApaarOrderByIdDesc(String subjectApaar);

        List<AuditEntry> findAllByOrderByIdAsc();
    }

    public interface EmailRepo extends JpaRepository<EmailMessage, Long> {
        List<EmailMessage> findTop100ByOrderByIdDesc();
    }

    public interface PolicyRepo extends JpaRepository<PolicySetting, String> {
    }

    public interface UserRepo extends JpaRepository<AppUser, Long> {
        Optional<AppUser> findByUsername(String username);
    }
}
