package com.sdms.prototype.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
public class UploadBatch {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    public Long institutionId;
    public String uploadedBy;
    public String fileName;
    public Instant createdAt;
    public int totalRows;
    public int accepted;
    public int updated;
    public int rejected;
    public int warnings;
}
