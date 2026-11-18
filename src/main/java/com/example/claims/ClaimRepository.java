package com.example.claims;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface ClaimRepository extends JpaRepository<Claim, UUID> {
    Page<Claim> findByClaimant(String claimant, Pageable page);
    Page<Claim> findByAssignee(String assignee, Pageable page);
}
