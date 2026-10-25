package com.sdms.prototype.domain;

import jakarta.persistence.*;

@Entity
public class BlockedRequester {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    public String apaarId;
    public Long requesterId;
}
