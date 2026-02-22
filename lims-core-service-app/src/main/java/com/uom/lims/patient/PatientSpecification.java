package com.uom.lims.patient;

import org.springframework.data.jpa.domain.Specification;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;

public class PatientSpecification {

    public static Specification<PatientEntity> filterPatients(
            String fullName,
            String phone,
            String identityNumber,
            String email,
            String branchCode,
            Boolean phoneVerified,
            Boolean emailVerified) {

        return (root, query, criteriaBuilder) -> {

            List<Predicate> predicates = new ArrayList<>();

            if (fullName != null && !fullName.isBlank()) {
                predicates.add(criteriaBuilder.like(
                        criteriaBuilder.lower(root.get("fullName")),
                        "%" + fullName.toLowerCase() + "%"));
            }

            if (phone != null && !phone.isBlank()) {
                predicates.add(criteriaBuilder.like(
                        root.get("phone"),
                        "%" + phone + "%"));
            }

            if (identityNumber != null && !identityNumber.isBlank()) {
                predicates.add(criteriaBuilder.equal(
                        root.get("identityNumber"),
                        identityNumber));
            }

            if (email != null && !email.isBlank()) {
                predicates.add(criteriaBuilder.equal(
                        root.get("email"),
                        email));
            }

            if (branchCode != null && !branchCode.isBlank()) {
                predicates.add(criteriaBuilder.equal(
                        root.get("branchCode"),
                        branchCode));
            }

            if (phoneVerified != null) {
                predicates.add(criteriaBuilder.equal(
                        root.get("phoneVerified"),
                        phoneVerified));
            }

            if (emailVerified != null) {
                predicates.add(criteriaBuilder.equal(
                        root.get("emailVerified"),
                        emailVerified));
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }
}
