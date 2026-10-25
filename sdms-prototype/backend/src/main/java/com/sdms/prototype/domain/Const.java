package com.sdms.prototype.domain;

import java.util.List;
import java.util.Set;

/** Shared constants. Plain strings are used instead of enums to keep the prototype simple. */
public final class Const {
    private Const() {}

    public static final String ROLE_ADMIN = "ADMIN";
    public static final String ROLE_INSTITUTION = "INSTITUTION";
    public static final String ROLE_STUDENT = "STUDENT";
    public static final String ROLE_REQUESTER = "REQUESTER";

    public static final Set<String> GENDERS = Set.of("MALE", "FEMALE", "OTHER");
    public static final Set<String> CATEGORIES = Set.of("GEN", "EWS", "OBC", "SC", "ST", "NA");
    public static final Set<String> SEAT_TYPES = Set.of("REGULAR", "SUPERNUMERARY");
    public static final Set<String> RESIDENCIES = Set.of("INDIAN", "NRI", "OCI", "FN");
    public static final Set<String> ADMISSION_MODES = Set.of("COUNSELLING", "MANAGEMENT", "DIRECT", "LATERAL");
    public static final Set<String> STATUSES = Set.of("ACTIVE", "WITHDRAWN", "GRADUATED");

    // APAAR check outcome stored on each enrolment record
    public static final String APAAR_VALID = "VALID";
    public static final String APAAR_MISSING = "MISSING";
    public static final String APAAR_INVALID_FORMAT = "INVALID_FORMAT";
    public static final String APAAR_NOT_FOUND = "NOT_FOUND";
    public static final String APAAR_MISMATCH = "MISMATCH";

    // Student confirmation
    public static final String CONF_PENDING = "PENDING";
    public static final String CONF_VERIFIED = "VERIFIED";
    public static final String CONF_DISPUTED = "DISPUTED";
    public static final String CONF_UNCONFIRMED = "UNCONFIRMED";
    public static final String CONF_REVIEWED = "REVIEWED";
    public static final String CONF_NOT_REQUIRED = "NOT_REQUIRED";

    // Access request lifecycle
    public static final String REQ_PENDING = "PENDING";
    public static final String REQ_APPROVED = "APPROVED";
    public static final String REQ_DENIED = "DENIED";
    public static final String REQ_EXPIRED = "EXPIRED";
    public static final String REQ_REVOKED = "REVOKED";

    public static final String TIER_VERIFIED = "VERIFIED";
    public static final String TIER_GENERAL = "GENERAL";

    public static final List<String> FIELDS_VERIFIED =
            List.of("enrolled", "institution", "course", "admissionYear", "status", "confirmation", "name");
    public static final List<String> FIELDS_GENERAL = List.of("enrolled", "institution");
}
