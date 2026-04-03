package com.zorvyn.finance.controller;
import com.zorvyn.finance.dto.response.ApiResponse;
import com.zorvyn.finance.entity.AuditLog;
import com.zorvyn.finance.entity.enums.AuditAction;
import com.zorvyn.finance.repository.AuditLogRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
/**
 * Audit log read endpoints — ADMIN only.
 *
 * The audit log is append-only — there are no POST, PUT, or DELETE
 * endpoints here. Entries are written by the service layer after every
 * state-changing operation.
 *
 * URL structure: /api/v1/audit-logs/**
 * All endpoints require ADMIN role (enforced in SecurityConfig + @PreAuthorize).
 *
 * This controller calls the repository directly — AuditLogService only
 * has a write method (log()), so reads go straight to the repository.
 */
@RestController
@RequestMapping("/v1/audit-logs")
@PreAuthorize("hasRole('ADMIN')")
public class AuditLogController {

    private final AuditLogRepository auditLogRepository;

    public AuditLogController(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    // ----------------------------------------------------------------
    // GET /api/v1/audit-logs  — paginated, all optional filters
    // ----------------------------------------------------------------

    /**
     * Paginated audit log with optional filtering.
     *
     * Query params (all optional):
     *   actorId    — filter by the UUID of the user who performed the action
     *   action     — filter by AuditAction enum value (RECORD_CREATED, USER_LOGIN, etc.)
     *   entityType — filter by entity class name ("User", "FinancialRecord")
     *   dateFrom   — inclusive lower bound on createdAt (yyyy-MM-dd)
     *   dateTo     — inclusive upper bound on createdAt (yyyy-MM-dd)
     *   page       — zero-based page index (default 0)
     *   size       — page size (default 20, max 100)
     *
     * Results are always newest-first (ORDER BY createdAt DESC in the repository query).
     *
     * dateFrom/dateTo are LocalDate params — we convert them to LocalDateTime
     * (start of day / end of day) before passing to the repository which
     * expects LocalDateTime for its BETWEEN comparison.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> getAuditLogs(
            @RequestParam(required = false) UUID         actorId,
            @RequestParam(required = false) AuditAction  action,
            @RequestParam(required = false) String       entityType,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {

        size = Math.min(size, 100);
        Pageable pageable = PageRequest.of(page, size);

        // Convert LocalDate to LocalDateTime for the repository query
        LocalDateTime fromDt = (dateFrom != null)
                ? dateFrom.atStartOfDay()
                : null;
        LocalDateTime toDt   = (dateTo != null)
                ? dateTo.atTime(LocalTime.MAX)
                : null;

        Page<AuditLog> auditPage = auditLogRepository.findAllWithFilters(
                actorId, action != null ? action.name() : null, entityType, fromDt, toDt, pageable);

        List<Map<String, Object>> content = auditPage.getContent()
                .stream()
                .map(this::toMap)
                .collect(Collectors.toList());

        // Build a paged response envelope manually
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("content",       content);
        response.put("page",          auditPage.getNumber());
        response.put("size",          auditPage.getSize());
        response.put("totalElements", auditPage.getTotalElements());
        response.put("totalPages",    auditPage.getTotalPages());
        response.put("first",         auditPage.isFirst());
        response.put("last",          auditPage.isLast());

        return ResponseEntity.ok(ApiResponse.ok(response, "Audit logs fetched successfully"));
    }

    // ----------------------------------------------------------------
    // GET /api/v1/audit-logs/entity/{entityType}/{entityId}
    // ----------------------------------------------------------------

    /**
     * Full change history for a specific entity — newest first.
     *
     * Example: GET /api/v1/audit-logs/entity/FinancialRecord/550e8400-...
     * Returns every create, update, and delete that touched that record.
     * Useful for the admin's "record history" detail view.
     */
    @GetMapping("/entity/{entityType}/{entityId}")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getEntityHistory(
            @PathVariable String entityType,
            @PathVariable UUID   entityId) {

        List<AuditLog> logs = auditLogRepository
                .findByEntityTypeAndEntityIdOrderByCreatedAtDesc(entityType, entityId);

        List<Map<String, Object>> result = logs.stream()
                .map(this::toMap)
                .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.ok(result, "Entity history fetched successfully"));
    }

    // ----------------------------------------------------------------
    // GET /api/v1/audit-logs/actor/{actorId}
    // ----------------------------------------------------------------

    /**
     * Paginated list of everything a specific user has done — newest first.
     * Useful when investigating whether a deactivated account made suspicious
     * changes before being locked out.
     */
    @GetMapping("/actor/{actorId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getActorHistory(
            @PathVariable UUID actorId,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {

        size = Math.min(size, 100);
        Pageable pageable = PageRequest.of(page, size);

        Page<AuditLog> auditPage = auditLogRepository
                .findByActorIdOrderByCreatedAtDesc(actorId, pageable);

        List<Map<String, Object>> content = auditPage.getContent()
                .stream()
                .map(this::toMap)
                .collect(Collectors.toList());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("content",       content);
        response.put("page",          auditPage.getNumber());
        response.put("size",          auditPage.getSize());
        response.put("totalElements", auditPage.getTotalElements());
        response.put("totalPages",    auditPage.getTotalPages());
        response.put("first",         auditPage.isFirst());
        response.put("last",          auditPage.isLast());

        return ResponseEntity.ok(
                ApiResponse.ok(response, "Actor history fetched successfully"));
    }

    // ----------------------------------------------------------------
    // Helper — map AuditLog entity to a plain Map for the response
    // ----------------------------------------------------------------

    /**
     * Maps an AuditLog entity to a response-safe Map.
     * We don't create a dedicated AuditLogResponse DTO here because
     * the audit log has no nested relations to protect — every field
     * is a primitive, an enum, a UUID, or a timestamp.
     */
    private Map<String, Object> toMap(AuditLog log) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id",          log.getId());
        map.put("actorId",     log.getActorId());
        map.put("actorName",   log.getActorName());
        map.put("action",      log.getAction());
        map.put("entityType",  log.getEntityType());
        map.put("entityId",    log.getEntityId());
        map.put("oldValue",    log.getOldValue());
        map.put("newValue",    log.getNewValue());
        map.put("ipAddress",   log.getIpAddress());
        map.put("createdAt",   log.getCreatedAt());
        return map;
    }
}