package com.sdms.prototype.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
public class ValidationIssue {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    public Long institutionId;
    public Long batchId;
    public int rowNumber;
    public String apaarId;
    public String severity;
    public String code;
    @Column(length = 600)
    public String message;
    public Instant createdAt;
}
