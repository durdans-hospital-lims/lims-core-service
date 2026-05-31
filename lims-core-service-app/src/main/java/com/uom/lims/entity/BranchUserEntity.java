package com.uom.lims.entity;

import com.uom.lims.api.branch.enums.AccountStatus;
import com.uom.lims.api.branch.enums.BranchUserRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * WHY: Stores the application-level profile of a staff member scoped to a branch.
 * Authentication credentials live in Keycloak; this entity captures the branch
 * association, role assignment, and account status that the LIMS uses for
 * access-control and reporting.
 */
@Getter
@Setter
@Entity
@Table(name = "branch_user")
public class BranchUserEntity extends BaseEntity {

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Column(name = "email", nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "phone", nullable = false, length = 20)
    private String phone;

    @Column(name = "username", nullable = false, unique = true, length = 50)
    private String username;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_status", nullable = false, length = 20)
    private AccountStatus accountStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 30)
    private BranchUserRole role;

    /**
     * WHY: Branch membership is stored as a code string (not a FK) so that
     * branch-scoped queries can use the same value that appears in JWT claims
     * and audit log entries without an extra join.
     */
    @Column(name = "branch_code", nullable = false, length = 50)
    private String branchCode;
}
