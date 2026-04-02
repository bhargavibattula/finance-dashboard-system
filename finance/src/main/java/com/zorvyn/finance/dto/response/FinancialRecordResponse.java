package com.zorvyn.finance.dto.response;

import com.zorvyn.finance.entity.FinancialRecord;
import com.zorvyn.finance.entity.enums.RecordCategory;
import com.zorvyn.finance.entity.enums.RecordType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response DTO for a single financial record.
 *
 * createdByName is resolved at mapping time from the JOIN FETCH
 * result in the repository — no lazy-load risk here.
 */
public class FinancialRecordResponse {

    private UUID           id;
    private BigDecimal     amount;
    private RecordType     type;
    private RecordCategory category;
    private LocalDate      recordDate;
    private String         notes;
    private String         createdByName;
    private String         lastModifiedByName;
    private LocalDateTime  createdAt;
    private LocalDateTime  updatedAt;

    private FinancialRecordResponse() {}

    public static FinancialRecordResponse from(FinancialRecord r) {
        FinancialRecordResponse dto = new FinancialRecordResponse();
        dto.id         = r.getId();
        dto.amount     = r.getAmount();
        dto.type       = r.getType();
        dto.category   = r.getCategory();
        dto.recordDate = r.getRecordDate();
        dto.notes      = r.getNotes();
        dto.createdAt  = r.getCreatedAt();
        dto.updatedAt  = r.getUpdatedAt();

        if (r.getCreatedBy() != null) {
            dto.createdByName = r.getCreatedBy().getFullName();
        }
        if (r.getLastModifiedBy() != null) {
            dto.lastModifiedByName = r.getLastModifiedBy().getFullName();
        }
        return dto;
    }

    public UUID           getId()                 { return id; }
    public BigDecimal     getAmount()             { return amount; }
    public RecordType     getType()               { return type; }
    public RecordCategory getCategory()           { return category; }
    public LocalDate      getRecordDate()         { return recordDate; }
    public String         getNotes()              { return notes; }
    public String         getCreatedByName()      { return createdByName; }
    public String         getLastModifiedByName() { return lastModifiedByName; }
    public LocalDateTime  getCreatedAt()          { return createdAt; }
    public LocalDateTime  getUpdatedAt()          { return updatedAt; }
}