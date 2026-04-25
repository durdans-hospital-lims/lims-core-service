package com.uom.lims.repository;

import com.uom.lims.api.branch.enums.AccountStatus;
import com.uom.lims.entity.BranchUserEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface BranchUserRepository extends JpaRepository<BranchUserEntity, UUID> {

    Page<BranchUserEntity> findAllByBranchCodeAndDeletedFalse(String branchCode, Pageable pageable);

    Optional<BranchUserEntity> findByIdAndBranchCodeAndDeletedFalse(UUID id, String branchCode);

    boolean existsByUsernameAndDeletedFalse(String username);

    boolean existsByEmailAndDeletedFalse(String email);

    boolean existsByUsernameAndIdNotAndDeletedFalse(String username, UUID id);

    boolean existsByEmailAndIdNotAndDeletedFalse(String email, UUID id);

    long countByBranchCodeAndDeletedFalse(String branchCode);

    long countByBranchCodeAndAccountStatusAndDeletedFalse(String branchCode, AccountStatus accountStatus);
}
