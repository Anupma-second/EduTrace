package com.sdms.prototype.domain;

import jakarta.persistence.*;

@Entity
public class Course {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    public Long institutionId;
    public String code;
    public String name;
    public String level;
    public int approvedIntake;
    public int supernumeraryLimit;
    public int durationYears;
}
