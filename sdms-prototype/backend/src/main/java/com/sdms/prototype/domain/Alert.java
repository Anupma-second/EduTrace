package com.sdms.prototype.domain;

import jakarta.persistence.*;
import java.time.Instant;

/** Enrolment alert with a plain-language explanation of why it fired. */
@Entity
public class Alert {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    public Long institutionId;
    public Long courseId;
    public int admissionYear;
    public String type;
    public String severity;
    @Column(length = 1000)
    public String message;
    public int currentValue;
    public int baselineValue;
    public String status;
    @Column(length = 500)
    public String note;
    public Instant createdAt;
    public Instant updatedAt;
}
