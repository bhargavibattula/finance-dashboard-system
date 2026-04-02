package com.zorvyn.finance.dto.request;

import com.zorvyn.finance.entity.enums.RecordCategory;
import com.zorvyn.finance.entity.enums.RecordType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Request body for PUT /api/v1/records/{id} (update).
 *
 * All fields are optional — the service applies only the non-null ones.
 * This lets the client send a partial update (e.g. fix only the amount)
 * without resending every field.
 */
public class UpdateRecordRequest {

    @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
    @Digits(integer = 15, fraction = 4,
            message = "Amount must have at most 15 integer digits and 4 decimal places")
    private BigDecimal amount;

    private RecordType type;

    private RecordCategory category;

    @PastOrPresent(message = "Record date cannot be in the future")
    private LocalDate recordDate;

    @Size(max = 1000, message = "Notes cannot exceed 1000 characters")
    private String notes;

    public UpdateRecordRequest() {}

    public BigDecimal      getAmount()     { return amount; }
    public void            setAmount(BigDecimal v)        { this.amount = v; }

    public RecordType      getType()       { return type; }
    public void            setType(RecordType v)          { this.type = v; }

    public RecordCategory  getCategory()   { return category; }
    public void            setCategory(RecordCategory v)  { this.category = v; }

    public LocalDate       getRecordDate() { return recordDate; }
    public void            setRecordDate(LocalDate v)     { this.recordDate = v; }

    public String          getNotes()      { return notes; }
    public void            setNotes(String v)             { this.notes = v; }
}