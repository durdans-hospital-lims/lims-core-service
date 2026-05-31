package com.uom.lims.api.branch.enums;

/**
 * WHY: Tracks whether a branch user's account is operational. A SUSPENDED account
 * retains its record for audit purposes while blocking active use; INACTIVE marks
 * accounts that have been decommissioned without deletion (soft-delete alternative
 * at the business-logic level).
 */
public enum AccountStatus {
    ACTIVE,
    INACTIVE,
    SUSPENDED
}
