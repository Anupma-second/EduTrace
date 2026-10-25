package com.sdms.prototype.domain;

import jakarta.persistence.*;
import java.time.LocalDate;

/** Stand-in for the national APAAR registry. In production this is an authorised API call. */
@Entity
public class ApaarPerson {
    @Id
    public String apaarId;
    public String name;
    public LocalDate dob;
    public String email;
    public boolean active = true;
}
