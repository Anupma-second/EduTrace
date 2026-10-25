package com.sdms.prototype.security;

/** The authenticated principal placed in the Spring Security context. */
public record AuthUser(String username, String role, Long institutionId, Long requesterId,
                       String apaarId, String displayName) {
}
