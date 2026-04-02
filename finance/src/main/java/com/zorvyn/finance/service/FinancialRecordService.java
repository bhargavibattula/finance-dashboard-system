package com.zorvyn.finance.service;

import com.zorvyn.finance.entity.enums.RecordCategory;
import com.zorvyn.finance.entity.enums.RecordType;
import com.zorvyn.finance.dto.request.CreateRecordRequest;
import com.zorvyn.finance.dto.request.UpdateRecordRequest;
import com.zorvyn.finance.dto.response.FinancialRecordResponse;
import com.zorvyn.finance.dto.response.PagedResponse;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Contract for financial record CRUD operations.
 */
public interface FinancialRecordService {

    /**
     * Create a new record linked to the given user.
     * Validates that the category is consistent with the type
     * (e.g. RENT cannot be used with INCOME).
     *
     * @param createdByUserId UUID of the user creating the record
     * @param ipAddress       forwarded to the audit log
     */
    FinancialRecordResponse createRecord(CreateRecordRequest request,
                                         UUID createdByUserId,
                                         String ipAddress);

    /**
     * Paginated, filtered list. Any filter may be null — null = no filter.
     * dateFrom and dateTo are inclusive bounds.
     */
    PagedResponse<FinancialRecordResponse> getRecords(RecordType     type,
                                                      RecordCategory category,
                                                      LocalDate      dateFrom,
                                                      LocalDate      dateTo,
                                                      String         search,
                                                      Pageable       pageable);

    /**
     * Fetch a single record by ID.
     * Throws ResourceNotFoundException if not found or soft-deleted.
     */
    FinancialRecordResponse getRecordById(UUID id);

    /**
     * Update an existing record. Only non-null fields are applied.
     * Tracks the modifier via lastModifiedBy.
     *
     * @param modifiedByUserId UUID of the user making the change
     * @param ipAddress        forwarded to the audit log
     */
    FinancialRecordResponse updateRecord(UUID id,
                                         UpdateRecordRequest request,
                                         UUID modifiedByUserId,
                                         String ipAddress);

    /**
     * Soft-delete a record by setting isDeleted = true.
     * The record disappears from all normal queries (@SQLRestriction
     * on the entity handles this automatically) but is never physically
     * removed from the database.
     *
     * @param deletedByUserId UUID of the user performing the delete
     * @param ipAddress       forwarded to the audit log
     */
    void deleteRecord(UUID id, UUID deletedByUserId, String ipAddress);
}