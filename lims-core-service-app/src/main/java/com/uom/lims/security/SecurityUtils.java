package com.uom.lims.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

public class SecurityUtils {

    public static String getCurrentBranchId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            return null;
        }

        // Try multiple common claim names
        String branchId = jwt.getClaimAsString("branch_id");
        if (branchId == null)
            branchId = jwt.getClaimAsString("branch_code");
        if (branchId == null)
            branchId = jwt.getClaimAsString("branch");
        if (branchId == null)
            branchId = jwt.getClaimAsString("branchCode");

        if (branchId == null || branchId.isBlank()) {
            System.err.println("WARNING: branch information is missing in JWT claims: " + jwt.getClaims().keySet());
            return null;
        }
        return branchId.trim().toUpperCase(); // Ensure consistency
    }

    public static String getCurrentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            return null;
        }

        return jwt.getClaimAsString("preferred_username");
    }
}
