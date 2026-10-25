package com.sdms.prototype.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
public class EmailMessage {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    public String toAddress;
    public String subject;
    @Column(length = 4000)
    public String body;
    public String kind;
    public Instant createdAt;
}
