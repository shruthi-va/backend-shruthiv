package com.example.claims;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import static com.example.claims.Claim.Status.*;

@Service
@Transactional
class ClaimService {
    private final ClaimRepository claims;

    ClaimService(ClaimRepository claims) {
        this.claims = claims;
    }

    ClaimView submit(String claimant, String policy, LocalDate date, String description) {
        return ClaimView.from(claims.save(new Claim(claimant, policy, date, description)), false, false);
    }

    @Transactional(readOnly = true)
    Page<ClaimSummary> list(Authentication user, Pageable page) {
        Page<Claim> visible;
        if (hasRole(user, "MANAGER")) {
            visible = claims.findAll(page);
        } else if (hasRole(user, "STAFF")) {
            visible = claims.findByAssignee(user.getName(), page);
        } else {
            visible = claims.findByClaimant(user.getName(), page);
        }
        return visible.map(ClaimSummary::from);
    }

    @Transactional(readOnly = true)
    ClaimView get(UUID id, Authentication user) {
        Claim claim = find(id);
        boolean manager = hasRole(user, "MANAGER");
        boolean staff = hasRole(user, "STAFF");
        if (!manager && !Objects.equals(user.getName(), staff ? claim.assignee : claim.claimant)) {
            throw notFound();
        }
        return ClaimView.from(claim, manager || staff, manager);
    }

    ClaimView assign(UUID id, String manager, String assignee) {
        Claim claim = find(id);
        if (!SecurityConfig.STAFF_USERNAME.equals(assignee)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown claims staff account");
        }
        requireStatus(claim, SUBMITTED);
        claim.assignee = assignee;
        claim.changeStatus(ASSIGNED, manager, Instant.now());
        return ClaimView.from(claim, true, true);
    }

    ClaimView review(UUID id, String staff, Claim.Decision recommendation, String reason) {
        Claim claim = find(id);
        if (!staff.equals(claim.assignee)) {
            throw notFound();
        }
        requireStatus(claim, ASSIGNED);
        claim.recommendation = recommendation;
        claim.reviewReason = reason;
        claim.changeStatus(REVIEWED, staff, Instant.now());
        return ClaimView.from(claim, true, false);
    }

    ClaimView decide(UUID id, String manager, Claim.Decision decision, String reason) {
        Claim claim = find(id);
        requireStatus(claim, REVIEWED);
        claim.decisionReason = reason;
        claim.changeStatus(decision == Claim.Decision.APPROVE ? APPROVED : DENIED, manager, Instant.now());
        return ClaimView.from(claim, true, true);
    }

    private Claim find(UUID id) {
        return claims.findById(id).orElseThrow(ClaimService::notFound);
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Claim not found");
    }

    private static void requireStatus(Claim claim, Claim.Status expected) {
        if (claim.status != expected) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Claim must be " + expected);
        }
    }

    private static boolean hasRole(Authentication user, String role) {
        return user.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_" + role));
    }

    record ClaimSummary(UUID id, String policyNumber, Claim.Status status, Instant submittedAt) {
        static ClaimSummary from(Claim claim) {
            return new ClaimSummary(claim.id, claim.policyNumber, claim.status, claim.submittedAt);
        }
    }

    record ClaimView(UUID id, String claimant, String policyNumber, LocalDate incidentDate,
                     String description, Instant submittedAt, Claim.Status status, String assignee,
                     Claim.Decision recommendation, String reviewReason, String decisionReason,
                     List<Claim.HistoryEntry> history) {
        static ClaimView from(Claim claim, boolean internal, boolean includeHistory) {
            return new ClaimView(claim.id, claim.claimant, claim.policyNumber, claim.incidentDate,
                    claim.description, claim.submittedAt, claim.status,
                    internal ? claim.assignee : null, internal ? claim.recommendation : null,
                    internal ? claim.reviewReason : null, claim.decisionReason,
                    includeHistory ? List.copyOf(claim.history) : null);
        }
    }
}
