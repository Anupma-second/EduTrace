package com.sdms.prototype.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
public class Requester {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    public String fullName;
    public String organisation;
    public String orgRegId;
    public String email;
    public String tier;
    public Instant verifiedAt;
    public Instant createdAt;
}
