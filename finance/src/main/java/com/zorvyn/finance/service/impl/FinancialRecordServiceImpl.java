package com.zorvyn.finance.service.impl;

import com.zorvyn.finance.entity.FinancialRecord;
import com.zorvyn.finance.entity.User;
import com.zorvyn.finance.entity.enums.AuditAction;
import com.zorvyn.finance.entity.enums.RecordCategory;
import com.zorvyn.finance.entity.enums.RecordType;
import com.zorvyn.finance.repository.FinancialRecordRepository;
import com.zorvyn.finance.repository.UserRepository;
import com.zorvyn.finance.dto.request.CreateRecordRequest;
import com.zorvyn.finance.dto.request.UpdateRecordRequest;
import com.zorvyn.finance.dto.response.FinancialRecordResponse;
import com.zorvyn.finance.dto.response.PagedResponse;
import com.zorvyn.finance.exception.BusinessException;
import com.zorvyn.finance.exception.ResourceNotFoundException;
import com.zorvyn.finance.service.AuditLogService;
import com.zorvyn.finance.service.FinancialRecordService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Core service for all financial record operations.
 *
 * Design decisions:
 *
 * 1. Category ↔ type validation.
 *    We defined one enum (RecordCategory) covering both income and expense
 *    categories. That keeps DB queries simple, but it means the service must
 *    reject nonsensical combinations like RENT on an INCOME record or SALARY
 *    on an EXPENSE record. Two static EnumSets make this O(1) and
 *    branch-free — no if/else chains.
 *
 * 2. Partial update in updateRecord().
 *    We apply a field only when the request carries a non-null value.
 *    This lets the client fix just the amount on a record without being
 *    forced to resend type, category, and date.
 *    We re-validate category ↔ type after merging, because the request
 *    might update type without changing category or vice versa.
 *
 * 3. Soft delete via entity method.
 *    FinancialRecord.softDelete(deletedBy) encapsulates the flag flip and
 *    the lastModifiedBy assignment in one place. The service just calls it
 *    and saves — no field-level knowledge of the soft-delete mechanism leaks
 *    into this layer.
 *
 * 4. Audit snapshots as compact strings.
 *    We capture the essential fields in a simple "key=value" string rather
 *    than full JSON. It is human-readable without a parser, contains no
 *    lazy-loaded fields, and keeps AuditLogService Jackson-free.
 *    The snapshot is built before any mutation so it truly represents the
 *    "before" state.
 *
 * 5. No Java-level loops for aggregation.
 *    Filtering happens entirely in the JPQL query inside
 *    FinancialRecordRepository. The service receives already-filtered Page
 *    results and maps them to DTOs — nothing more.
 */
@Service
public class FinancialRecordServiceImpl implements FinancialRecordService {

    // ----------------------------------------------------------------
    // Category classification — used for cross-field validation
    // ----------------------------------------------------------------

    private static final Set<RecordCategory> INCOME_CATEGORIES = EnumSet.of(
            RecordCategory.SALARY,
            RecordCategory.FREELANCE,
            RecordCategory.INVESTMENT,
            RecordCategory.OTHER_INCOME
    );

    private static final Set<RecordCategory> EXPENSE_CATEGORIES = EnumSet.of(
            RecordCategory.FOOD,
            RecordCategory.RENT,
            RecordCategory.UTILITIES,
            RecordCategory.TRANSPORT,
            RecordCategory.HEALTHCARE,
            RecordCategory.ENTERTAINMENT,
            RecordCategory.EDUCATION,
            RecordCategory.SUBSCRIPTIONS,
            RecordCategory.OTHER_EXPENSE
    );

    private final FinancialRecordRepository recordRepository;
    private final UserRepository            userRepository;
    private final AuditLogService           auditLogService;

    public FinancialRecordServiceImpl(FinancialRecordRepository recordRepository,
                                      UserRepository            userRepository,
                                      AuditLogService           auditLogService) {
        this.recordRepository = recordRepository;
        this.userRepository   = userRepository;
        this.auditLogService  = auditLogService;
    }

    // ----------------------------------------------------------------
    // Create
    // ----------------------------------------------------------------

    @Override
    @Transactional
    public FinancialRecordResponse createRecord(CreateRecordRequest request,
                                                UUID                createdByUserId,
                                                String              ipAddress) {

        validateCategoryMatchesType(request.getCategory(), request.getType());

        User creator = findUserOrThrow(createdByUserId);

        FinancialRecord record = new FinancialRecord(
                request.getAmount(),
                request.getType(),
                request.getCategory(),
                request.getRecordDate(),
                request.getNotes(),
                creator
        );

        FinancialRecord saved = recordRepository.save(record);

        auditLogService.log(
                creator.getId(),
                creator.getFullName(),
                AuditAction.RECORD_CREATED,
                "FinancialRecord",
                saved.getId(),
                null,
                snapshotRecord(saved),
                ipAddress
        );

        return FinancialRecordResponse.from(saved);
    }

    // ----------------------------------------------------------------
    // Read — paginated + filtered list
    // ----------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<FinancialRecordResponse> getRecords(RecordType     type,
                                                             RecordCategory category,
                                                             LocalDate      dateFrom,
                                                             LocalDate      dateTo,
                                                             String         search,
                                                             Pageable       pageable) {
        if (dateFrom != null && dateTo != null && dateFrom.isAfter(dateTo)) {
            throw new BusinessException(
                    "dateFrom (" + dateFrom + ") must not be after dateTo (" + dateTo + ")");
        }

        Page<FinancialRecord> page = recordRepository.findAllWithFilters(
                type, category, dateFrom, dateTo, search, pageable);

        List<FinancialRecordResponse> content = page.getContent()
                .stream()
                .map(FinancialRecordResponse::from)
                .toList();

        return PagedResponse.of(page, content);
    }

    // ----------------------------------------------------------------
    // Read — single record
    // ----------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public FinancialRecordResponse getRecordById(UUID id) {
        return FinancialRecordResponse.from(findRecordOrThrow(id));
    }

    // ----------------------------------------------------------------
    // Update — partial, only apply non-null fields
    // ----------------------------------------------------------------

    @Override
    @Transactional
    public FinancialRecordResponse updateRecord(UUID                id,
                                                UpdateRecordRequest request,
                                                UUID                modifiedByUserId,
                                                String              ipAddress) {

        FinancialRecord record     = findRecordOrThrow(id);
        String          oldSnapshot = snapshotRecord(record);

        // Apply only the fields the caller actually sent
        if (request.getAmount() != null) {
            record.setAmount(request.getAmount());
        }
        if (request.getType() != null) {
            record.setType(request.getType());
        }
        if (request.getCategory() != null) {
            record.setCategory(request.getCategory());
        }
        if (request.getRecordDate() != null) {
            record.setRecordDate(request.getRecordDate());
        }
        if (request.getNotes() != null) {
            record.setNotes(request.getNotes());
        }

        // Re-validate after merge — type and category may have been changed
        // independently in the same request or across separate updates
        validateCategoryMatchesType(record.getCategory(), record.getType());

        User modifier = findUserOrThrow(modifiedByUserId);
        record.setLastModifiedBy(modifier);

        FinancialRecord saved = recordRepository.save(record);

        auditLogService.log(
                modifier.getId(),
                modifier.getFullName(),
                AuditAction.RECORD_UPDATED,
                "FinancialRecord",
                saved.getId(),
                oldSnapshot,
                snapshotRecord(saved),
                ipAddress
        );

        return FinancialRecordResponse.from(saved);
    }

    // ----------------------------------------------------------------
    // Soft delete
    // ----------------------------------------------------------------

    @Override
    @Transactional
    public void deleteRecord(UUID id, UUID deletedByUserId, String ipAddress) {
        FinancialRecord record      = findRecordOrThrow(id);
        String          oldSnapshot = snapshotRecord(record);

        User deleter = findUserOrThrow(deletedByUserId);

        // softDelete() sets isDeleted=true and lastModifiedBy=deleter
        record.softDelete(deleter);
        recordRepository.save(record);

        auditLogService.log(
                deleter.getId(),
                deleter.getFullName(),
                AuditAction.RECORD_DELETED,
                "FinancialRecord",
                record.getId(),
                oldSnapshot,
                null,
                ipAddress
        );
    }

    // ----------------------------------------------------------------
    // Private helpers
    // ----------------------------------------------------------------

    private FinancialRecord findRecordOrThrow(UUID id) {
        return recordRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("FinancialRecord", id));
    }

    private User findUserOrThrow(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));
    }

    /**
     * Guards against combinations that are logically wrong:
     *   INCOME record with RENT category — makes no sense
     *   EXPENSE record with SALARY category — makes no sense
     *
     * Uses EnumSet.contains() which is O(1) — no iteration, no streams.
     */
    private void validateCategoryMatchesType(RecordCategory category, RecordType type) {
        if (type == RecordType.INCOME && EXPENSE_CATEGORIES.contains(category)) {
            throw new BusinessException(
                    "Category " + category + " is an expense category "
                            + "and cannot be used on an INCOME record");
        }
        if (type == RecordType.EXPENSE && INCOME_CATEGORIES.contains(category)) {
            throw new BusinessException(
                    "Category " + category + " is an income category "
                            + "and cannot be used on an EXPENSE record");
        }
    }

    /**
     * Compact, human-readable snapshot for the audit log.
     * Built from fields already in memory — no extra DB call, no lazy load.
     */
    private String snapshotRecord(FinancialRecord r) {
        return "amount="    + r.getAmount()
                + ", type="      + r.getType()
                + ", category="  + r.getCategory()
                + ", date="      + r.getRecordDate()
                + ", notes="     + r.getNotes()
                + ", deleted="   + r.isDeleted();
    }
}