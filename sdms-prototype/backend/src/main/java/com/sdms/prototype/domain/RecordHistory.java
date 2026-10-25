package com.sdms.prototype.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
public class RecordHistory {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    public Long recordId;
    public Long institutionId;
    public String fieldName;
    public String oldValue;
    public String newValue;
    @Column(length = 500)
    public String reason;
    public String changedBy;
    public Instant changedAt;
}
