package com.sdms.prototype.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(indexes = {@Index(columnList = "apaar_id"), @Index(columnList = "requester_id")})
public class AccessRequest {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    public Long requesterId;
    public String apaarId;
    @Column(length = 500)
    public String purpose;
    public String fields;
    public int durationDays;
    public String status;
    public String approvedFields;
    public Instant createdAt;
    public Instant decideBy;
    public Instant decidedAt;
    public Instant expiresAt;
    public int viewCount;
}
