package com.example.claims;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
class Claim {
    public enum Status { SUBMITTED, ASSIGNED, REVIEWED, APPROVED, DENIED }
    public enum Decision { APPROVE, DENY }

    @Id
    @GeneratedValue
    UUID id;

    @Version
    long version;

    @Column(nullable = false)
    String claimant;

    @Column(nullable = false, length = 80)
    String policyNumber;

    @Column(nullable = false)
    LocalDate incidentDate;

    @Column(nullable = false, length = 2000)
    String description;

    @Column(nullable = false)
    Instant submittedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    Status status;

    String assignee;

    @Enumerated(EnumType.STRING)
    Decision recommendation;

    @Column(length = 1000)
    String reviewReason;

    @Column(length = 1000)
    String decisionReason;

    @ElementCollection
    @CollectionTable(name = "claim_history", joinColumns = @JoinColumn(name = "claim_id"))
    @OrderColumn(name = "event_index")
    List<HistoryEntry> history = new ArrayList<>();

    protected Claim() {}

    Claim(String claimant, String policyNumber, LocalDate incidentDate, String description) {
        this.claimant = claimant;
        this.policyNumber = policyNumber;
        this.incidentDate = incidentDate;
        this.description = description;
        this.submittedAt = Instant.now();
        changeStatus(Status.SUBMITTED, claimant, submittedAt);
    }

    void changeStatus(Status next, String actor, Instant at) {
        status = next;
        history.add(new HistoryEntry(next, actor, at));
    }

    @Embeddable
    record HistoryEntry(
            @Enumerated(EnumType.STRING) @Column(nullable = false) Status status,
            @Column(nullable = false) String actor,
            @Column(nullable = false) Instant occurredAt) {}
}
