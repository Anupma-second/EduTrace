package com.sdms.prototype.domain;

import jakarta.persistence.*;

@Entity
public class Institution {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(unique = true)
    public String aicteId;
    public String name;
    public String state;
    public String district;
}
