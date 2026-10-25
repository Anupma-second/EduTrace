package com.sdms.prototype.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(indexes = {
        @Index(columnList = "apaar_id"),
        @Index(columnList = "institution_id,admission_year"),
        @Index(columnList = "course_id,admission_year")
})
public class EnrolmentRecord {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    public String apaarId;
    public String rollNo;
    public String name;
    public LocalDate dob;
    public String gender;
    public Long institutionId;
    public Long courseId;
    public int admissionYear;
    public String category;
    public String seatType;
    public String residency;
    public String admissionMode;
    public String status;
    public String apaarStatus;
    public String confirmationStatus;
    @Column(length = 500)
    public String confirmationNote;
    public String flags;
    public Long batchId;
    public Instant createdAt;
    public Instant updatedAt;
}
