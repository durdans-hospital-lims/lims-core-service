package com.uom.lims.api.branch.enums;

/**
 * WHY: Enumerates the staff roles that a Branch Admin can assign when creating or updating
 * branch users. BRANCH_ADMIN and SUPER_ADMIN are intentionally excluded — those are
 * system-level roles that must be granted by a Super Admin, not delegated by branch-level staff.
 */
public enum BranchUserRole {
    FRONT_DESK,
    BILLING_OFFICER,
    PHLEBOTOMIST,
    LAB_RECEPTIONIST,
    MLT,
    LAB_SUPERVISOR
}
