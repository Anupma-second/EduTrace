package com.sdms.prototype.domain;

import jakarta.persistence.*;

@Entity
public class AppUser {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(unique = true)
    public String username;
    public String passwordHash;
    public String role;
    public Long institutionId;
    public Long requesterId;
    public String fullName;
    public String email;
    public boolean otpVerified = true;
}
