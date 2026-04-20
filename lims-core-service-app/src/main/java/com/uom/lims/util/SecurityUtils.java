package com.uom.lims.util;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * WHY: Clinical actions in a hospital LIMS carry medicolegal weight.
 * Every order, sample collection, and payment must be traceable to the
 * authenticated user who performed the action for audit and accountability.
 * This utility extracts identity claims directly from the Keycloak JWT so
 * that services never need to depend on SecurityContextHolder directly.
 */
@Component
public class SecurityUtils {

    /**
     * WHY: The Keycloak subject ('sub') is a stable UUID that uniquely
     * identifies the authenticated user across all service calls.
     * Using the subject — rather than a username — guards against
     * identity corruption if usernames are ever changed in Keycloak.
     *
     * @return the Keycloak subject UUID as a String, or "system" if unauthenticated
     */
    public String getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtToken) {
            Object sub = jwtToken.getTokenAttributes().get("sub");
            return sub != null ? sub.toString() : "system";
        }
        return "system";
    }

    /**
     * WHY: The 'preferred_username' claim holds the human-readable Keycloak
     * username (e.g., staff login ID). It is used in audit log messages and
     * display fields where a UUID would be unreadable by clinical staff.
     *
     * @return the preferred_username claim, or "system" if unauthenticated
     */
    public String getCurrentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtToken) {
            Object username = jwtToken.getTokenAttributes().get("preferred_username");
            return username != null ? username.toString() : "system";
        }
        return "system";
    }
}
