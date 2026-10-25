package com.sdms.prototype.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
public class DuplicateCase {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    public String apaarId;
    public Long recordAId;
    public Long recordBId;
    public Long institutionAId;
    public Long institutionBId;
    public String status;
    public String resolution;
    @Column(length = 500)
    public String note;
    @Column(length = 500)
    public String responseA;
    @Column(length = 500)
    public String responseB;
    public Instant createdAt;
    public Instant deadline;
    public Instant resolvedAt;
}
