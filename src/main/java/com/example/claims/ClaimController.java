package com.example.claims;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
class ClaimController {
    private final ClaimService claims;

    ClaimController(ClaimService claims) {
        this.claims = claims;
    }

    @GetMapping("/csrf")
    CsrfToken csrf(CsrfToken token) {
        return token;
    }

    @PostMapping("/claims")
    ResponseEntity<ClaimService.ClaimView> submit(Authentication user, @Valid @RequestBody Submission input) {
        var claim = claims.submit(user.getName(), input.policyNumber(), input.incidentDate(), input.description());
        return ResponseEntity.created(URI.create("/api/claims/" + claim.id())).body(claim);
    }

    @GetMapping("/claims")
    PagedModel<ClaimService.ClaimSummary> list(Authentication user, @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Page must be non-negative and size 1-100");
        }
        Page<ClaimService.ClaimSummary> result = claims.list(user,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "submittedAt", "id")));
        return new PagedModel<>(result);
    }

    @GetMapping("/claims/{id}")
    ClaimService.ClaimView get(Authentication user, @PathVariable UUID id) {
        return claims.get(id, user);
    }

    @PostMapping("/claims/{id}/assignment")
    ClaimService.ClaimView assign(Authentication user, @PathVariable UUID id,
                                  @Valid @RequestBody Assignment input) {
        return claims.assign(id, user.getName(), input.assignee());
    }

    @PostMapping("/claims/{id}/review")
    ClaimService.ClaimView review(Authentication user, @PathVariable UUID id,
                                  @Valid @RequestBody Review input) {
        return claims.review(id, user.getName(), input.recommendation(), input.reason());
    }

    @PostMapping("/claims/{id}/decision")
    ClaimService.ClaimView decide(Authentication user, @PathVariable UUID id,
                                  @Valid @RequestBody FinalDecision input) {
        return claims.decide(id, user.getName(), input.decision(), input.reason());
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ProblemDetail concurrentUpdate() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Claim changed concurrently; reload it");
    }

    record Submission(@NotBlank @Size(max = 80) String policyNumber,
                      @NotNull @PastOrPresent LocalDate incidentDate,
                      @NotBlank @Size(max = 2000) String description) {}

    record Assignment(@NotBlank @Size(max = 80) String assignee) {}

    record Review(@NotNull Claim.Decision recommendation, @NotBlank @Size(max = 1000) String reason) {}

    record FinalDecision(@NotNull Claim.Decision decision, @NotBlank @Size(max = 1000) String reason) {}
}
