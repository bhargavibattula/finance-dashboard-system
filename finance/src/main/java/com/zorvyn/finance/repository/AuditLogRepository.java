package com.zorvyn.finance.repository;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import com.zorvyn.finance.entity.AuditLog;
import com.zorvyn.finance.entity.enums.AuditAction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;





















@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    // ----------------------------------------------------------------
    // Admin audit log viewer — paginated list with optional filters
    // ----------------------------------------------------------------

    /**
     * Paginated audit log with optional filtering by actor, action,
     * entity type, and a date range.
     *
     * All filter parameters are nullable — passing null for a parameter
     * effectively removes that filter condition from the query.
     *
     * Results are always returned newest-first (ORDER BY createdAt DESC)
     * because the most recent events are the most relevant when
     * investigating an issue.
     */
    @Query("""
            SELECT a FROM AuditLog a
            WHERE (:actorId    IS NULL OR a.actorId    = :actorId)
              AND (:action     IS NULL OR a.action     = :action)
              AND (:entityType IS NULL OR a.entityType = :entityType)
              AND (:from       IS NULL OR a.createdAt  >= :from)
              AND (:to         IS NULL OR a.createdAt  <= :to)
            ORDER BY a.createdAt DESC
            """)
    Page<AuditLog> findAllWithFilters(
            @Param("actorId")    UUID          actorId,
            @Param("action")     AuditAction   action,
            @Param("entityType") String        entityType,
            @Param("from")       LocalDateTime from,
            @Param("to")         LocalDateTime to,
            Pageable pageable
    );

    // ----------------------------------------------------------------
    // Entity-scoped history — "show me everything that happened to X"
    // ----------------------------------------------------------------

    /**
     * Full history of changes for a specific entity, newest first.
     *
     * Example use: an admin opens a FinancialRecord detail view and sees
     * a timeline of every create/update/delete that touched it.
     */
    List<AuditLog> findByEntityTypeAndEntityIdOrderByCreatedAtDesc(
            String entityType,
            UUID   entityId
    );

    // ----------------------------------------------------------------
    // Actor-scoped history — "show me everything actor X did"
    // ----------------------------------------------------------------

    /**
     * Paginated list of all audit events performed by a specific user.
     * Useful for investigating whether a deactivated account made
     * suspicious changes before being locked out.
     */
    Page<AuditLog> findByActorIdOrderByCreatedAtDesc(UUID actorId, Pageable pageable);

    // ----------------------------------------------------------------
    // Recent logins — security monitoring
    // ----------------------------------------------------------------

    /**
     * Fetch the most recent login events for a user.
     * Called by the admin when reviewing a user's access history.
     * Pageable lets the caller cap the result (e.g. last 10 logins).
     */
    @Query("""
            SELECT a FROM AuditLog a
            WHERE a.actorId = :actorId
              AND a.action  = com.zorvyn.finance.entity.enums.AuditAction.USER_LOGIN
            ORDER BY a.createdAt DESC
            """)
    Page<AuditLog> findRecentLoginsByActor(
            @Param("actorId") UUID actorId,
            Pageable pageable
    );
}

