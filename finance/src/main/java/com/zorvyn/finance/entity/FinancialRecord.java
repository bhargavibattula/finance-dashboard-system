package com.zorvyn.finance.entity;
import com.zorvyn.finance.entity.enums.RecordCategory;
import com.zorvyn.finance.entity.enums.RecordType;
import jakarta.persistence.*;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A single financial transaction in the system.
 *
 * Key design decisions:
 *
 * 1. amount is BigDecimal — never Float/Double for money.
 *    precision=19, scale=4 handles large values and sub-cent precision
 *    without floating-point rounding errors.
 *
 * 2. recordDate is LocalDate, not LocalDateTime. Transactions belong to
 *    a calendar date; the exact time of entry is captured by createdAt
 *    in BaseEntity if ever needed.
 *
 * 3. Soft delete via isDeleted flag + @SQLRestriction.
 *    @SQLRestriction appends "is_deleted = false" to every JPA query
 *    automatically — deleted records are invisible to all normal queries
 *    without any filter code in the service layer.
 *
 * 4. createdBy / lastModifiedBy store the user directly (not just an ID)
 *    so joins to the user table are handled by JPA and the service layer
 *    can access user details without a secondary query.
 */
@Entity
@Table(name = "financial_records")
@SQLRestriction("is_deleted = false")
public class FinancialRecord extends BaseEntity {

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "record_type", nullable = false, length = 20)
    private RecordType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RecordCategory category;

    @Column(name = "record_date", nullable = false)
    private LocalDate recordDate;

    @Column(length = 1000)
    private String notes;

    @Column(name = "is_deleted", nullable = false)
    private boolean isDeleted = false;

    /**
     * The user who originally created this record.
     * LAZY because we rarely need full user details when listing records.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false, updatable = false)
    private User createdBy;

    /**
     * The user who last updated this record.
     * Null until the first update after creation.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "last_modified_by")
    private User lastModifiedBy;

    // ----------------------------------------------------------------
    // Constructors
    // ----------------------------------------------------------------

    protected FinancialRecord() {}

    public FinancialRecord(BigDecimal amount, RecordType type,
                           RecordCategory category, LocalDate recordDate,
                           String notes, User createdBy) {
        this.amount     = amount;
        this.type       = type;
        this.category   = category;
        this.recordDate = recordDate;
        this.notes      = notes;
        this.createdBy  = createdBy;
        this.isDeleted  = false;
    }

    // ----------------------------------------------------------------
    // Business method — keeps delete logic inside the entity
    // ----------------------------------------------------------------

    public void softDelete(User deletedBy) {
        this.isDeleted       = true;
        this.lastModifiedBy  = deletedBy;
    }

    // ----------------------------------------------------------------
    // Getters and setters
    // ----------------------------------------------------------------

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public RecordType getType() { return type; }
    public void setType(RecordType type) { this.type = type; }

    public RecordCategory getCategory() { return category; }
    public void setCategory(RecordCategory category) { this.category = category; }

    public LocalDate getRecordDate() { return recordDate; }
    public void setRecordDate(LocalDate recordDate) { this.recordDate = recordDate; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public boolean isDeleted() { return isDeleted; }

    public User getCreatedBy() { return createdBy; }

    public User getLastModifiedBy() { return lastModifiedBy; }
    public void setLastModifiedBy(User lastModifiedBy) { this.lastModifiedBy = lastModifiedBy; }
}
