package com.sdms.prototype.domain;

import jakarta.persistence.*;
import java.time.Instant;

/** Append-only, hash-chained audit log. */
@Entity
@Table(indexes = {@Index(columnList = "subject_apaar")})
public class AuditEntry {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    public Instant createdAt;
    public String actor;
    public String role;
    public String action;
    public String target;
    public String subjectApaar;
    @Column(length = 1000)
    public String details;
    public String prevHash;
    public String hash;
}
