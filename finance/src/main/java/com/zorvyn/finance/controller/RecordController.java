package com.zorvyn.finance.controller;
import com.zorvyn.finance.dto.request.CreateRecordRequest;
import com.zorvyn.finance.dto.request.UpdateRecordRequest;
import com.zorvyn.finance.dto.response.ApiResponse;
import com.zorvyn.finance.dto.response.FinancialRecordResponse;
import com.zorvyn.finance.dto.response.PagedResponse;
import com.zorvyn.finance.entity.User;
import com.zorvyn.finance.entity.enums.RecordCategory;
import com.zorvyn.finance.entity.enums.RecordType;
import com.zorvyn.finance.service.FinancialRecordService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Financial record CRUD endpoints.
 *
 * URL structure: /api/v1/records/**
 *
 * Role rules (matching SecurityConfig):
 *   POST   /v1/records          → ANALYST + ADMIN (create)
 *   GET    /v1/records          → ALL authenticated (list + filter)
 *   GET    /v1/records/{id}     → ALL authenticated (single record)
 *   PUT    /v1/records/{id}     → ANALYST + ADMIN (update)
 *   DELETE /v1/records/{id}     → ADMIN only (soft delete)
 *
 * The authenticated user's ID is passed to the service for createdBy /
 * lastModifiedBy tracking and audit log entries.
 */
@RestController
@RequestMapping("/v1/records")
public class RecordController {

    private final FinancialRecordService recordService;

    public RecordController(FinancialRecordService recordService) {
        this.recordService = recordService;
    }

    // ----------------------------------------------------------------
    // POST /api/v1/records  — ANALYST + ADMIN
    // ----------------------------------------------------------------

    /**
     * Create a new financial record.
     * The authenticated user becomes the record's createdBy.
     * Returns 201 Created.
     *
     * Business rules enforced in the service:
     *   - Amount must be > 0 (also validated by @Valid)
     *   - Category must match the record type (e.g. RENT ≠ INCOME)
     *   - recordDate cannot be in the future (also validated by @Valid)
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('ANALYST', 'ADMIN')")
    public ResponseEntity<ApiResponse<FinancialRecordResponse>> createRecord(
            @Valid @RequestBody CreateRecordRequest request,
            @AuthenticationPrincipal User currentUser,
            HttpServletRequest httpRequest) {

        FinancialRecordResponse created = recordService.createRecord(
                request, currentUser.getId(), extractIp(httpRequest));

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.ok(created, "Record created successfully"));
    }

    // ----------------------------------------------------------------
    // GET /api/v1/records  — ALL authenticated, paginated + filtered
    // ----------------------------------------------------------------

    /**
     * Returns a paginated, optionally filtered list of financial records.
     *
     * All filter params are optional — omit any to remove that filter:
     *   type      — INCOME or EXPENSE
     *   category  — any RecordCategory enum value
     *   dateFrom  — inclusive lower bound (yyyy-MM-dd)
     *   dateTo    — inclusive upper bound (yyyy-MM-dd)
     *   search    — partial case-insensitive match on notes
     *   page      — zero-based page index (default 0)
     *   size      — page size (default 20, max 100)
     *   sortBy    — field to sort on (default "recordDate")
     *   sortDir   — "asc" or "desc" (default "desc")
     *
     * @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) tells Spring to parse
     * "2024-01-15" as a LocalDate automatically from the query string.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<FinancialRecordResponse>>> getRecords(
            @RequestParam(required = false) RecordType    type,
            @RequestParam(required = false) RecordCategory category,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0")   int page,
            @RequestParam(defaultValue = "20")  int size,
            @RequestParam(defaultValue = "recordDate") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {

        size = Math.min(size, 100);
        Sort sort = sortDir.equalsIgnoreCase("asc")
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);

        PagedResponse<FinancialRecordResponse> result =
                recordService.getRecords(type, category, dateFrom, dateTo, search, pageable);

        return ResponseEntity.ok(ApiResponse.ok(result, "Records fetched successfully"));
    }

    // ----------------------------------------------------------------
    // GET /api/v1/records/{id}  — ALL authenticated
    // ----------------------------------------------------------------

    /**
     * Fetch a single record by its UUID.
     * Soft-deleted records are invisible (@SQLRestriction on the entity).
     * Returns 404 if not found or already deleted.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<FinancialRecordResponse>> getRecordById(
            @PathVariable UUID id) {

        FinancialRecordResponse record = recordService.getRecordById(id);
        return ResponseEntity.ok(ApiResponse.ok(record, "Record fetched successfully"));
    }

    // ----------------------------------------------------------------
    // PUT /api/v1/records/{id}  — ANALYST + ADMIN
    // ----------------------------------------------------------------

    /**
     * Update an existing record.
     * Only the fields present in the request body are updated —
     * null fields are ignored (partial update pattern).
     * The authenticated user is tracked as lastModifiedBy.
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ANALYST', 'ADMIN')")
    public ResponseEntity<ApiResponse<FinancialRecordResponse>> updateRecord(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateRecordRequest request,
            @AuthenticationPrincipal User currentUser,
            HttpServletRequest httpRequest) {

        FinancialRecordResponse updated = recordService.updateRecord(
                id, request, currentUser.getId(), extractIp(httpRequest));

        return ResponseEntity.ok(ApiResponse.ok(updated, "Record updated successfully"));
    }

    // ----------------------------------------------------------------
    // DELETE /api/v1/records/{id}  — ADMIN only (soft delete)
    // ----------------------------------------------------------------

    /**
     * Soft-deletes a record by setting its isDeleted flag to true.
     * The record is not physically removed from the database.
     * @SQLRestriction on the entity hides it from all future queries.
     * Returns 200 OK (not 204) to keep the response shape consistent
     * with every other endpoint — frontend gets a message confirmation.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteRecord(
            @PathVariable UUID id,
            @AuthenticationPrincipal User currentUser,
            HttpServletRequest httpRequest) {

        recordService.deleteRecord(id, currentUser.getId(), extractIp(httpRequest));
        return ResponseEntity.ok(ApiResponse.ok("Record deleted successfully"));
    }

    // ----------------------------------------------------------------
    // Helper
    // ----------------------------------------------------------------

    private String extractIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}