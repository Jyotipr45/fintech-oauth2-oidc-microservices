/*
 * Copyright (c) 2026 Jyotiprakash Parida
 * SPDX-License-Identifier: MIT
 */
package com.jyotiprakash.banking.transfer.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Transfer aggregate. {@code (ownerSubject, idempotencyKey)} is unique, so a retried request can never
 * create a second transfer.
 *
 * @author Jyotiprakash Parida
 */
@Entity
@Table(name = "transfers")
public class Transfer {

    @Id
    private UUID id;

    @Column(name = "owner_subject", nullable = false, updatable = false, length = 64)
    private String ownerSubject;

    @Column(name = "idempotency_key", nullable = false, updatable = false, length = 64)
    private String idempotencyKey;

    @Column(name = "source_account_id", nullable = false, updatable = false)
    private UUID sourceAccountId;

    @Column(name = "target_account_number", nullable = false, updatable = false, length = 20)
    private String targetAccountNumber;

    @Column(name = "amount", nullable = false, updatable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, updatable = false, length = 3)
    private String currency;

    @Column(name = "reference", length = 140, updatable = false)
    private String reference;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private TransferStatus status;

    @Column(name = "failure_code", length = 64)
    private String failureCode;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Transfer() {
        // for JPA
    }

    private Transfer(String ownerSubject, String idempotencyKey, UUID sourceAccountId, String targetAccountNumber,
                     BigDecimal amount, String currency, String reference) {
        this.id = UUID.randomUUID();
        this.ownerSubject = ownerSubject;
        this.idempotencyKey = idempotencyKey;
        this.sourceAccountId = sourceAccountId;
        this.targetAccountNumber = targetAccountNumber;
        this.amount = amount;
        this.currency = currency;
        this.reference = reference;
        this.status = TransferStatus.PENDING;
    }

    public static Transfer pending(String ownerSubject, String idempotencyKey, UUID sourceAccountId,
                                   String targetAccountNumber, BigDecimal amount, String currency, String reference) {
        return new Transfer(ownerSubject, idempotencyKey, sourceAccountId, targetAccountNumber, amount, currency,
                reference);
    }

    public boolean sameRequestAs(UUID otherSource, String otherTarget, BigDecimal otherAmount, String otherCurrency) {
        return sourceAccountId.equals(otherSource)
                && targetAccountNumber.equals(otherTarget)
                && amount.compareTo(otherAmount) == 0
                && currency.equals(otherCurrency);
    }

    public void markCompleted() {
        this.status = TransferStatus.COMPLETED;
        this.failureCode = null;
    }

    public void markFailed(String code) {
        this.status = TransferStatus.FAILED;
        this.failureCode = code;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getOwnerSubject() {
        return ownerSubject;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public UUID getSourceAccountId() {
        return sourceAccountId;
    }

    public String getTargetAccountNumber() {
        return targetAccountNumber;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public String getReference() {
        return reference;
    }

    public TransferStatus getStatus() {
        return status;
    }

    public String getFailureCode() {
        return failureCode;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
