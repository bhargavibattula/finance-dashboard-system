package com.zorvyn.finance.service;

import com.zorvyn.finance.entity.enums.AuditAction;

import java.util.UUID;

/**
 * Contract for writing to the audit trail.
 *
 * Deliberately simple — one method, called after every state change.
 * The service resolves the current actor from the call arguments;
 * no security context dependency here, keeping it easy to test and
 * easy to call from both authenticated and system contexts.
 */
public interface AuditLogService {

    /**
     * Persist one audit entry.
     *
     * @param actorId    UUID of the user performing the action; may be null
     *                   for system-generated events
     * @param actorName  full name of the actor at the time of the action;
     *                   captured as a snapshot so renames don't alter history
     * @param action     the AuditAction enum value describing what happened
     * @param entityType simple name of the affected class, e.g. "User" or
     *                   "FinancialRecord"
     * @param entityId   primary key of the affected record; null for login events
     * @param oldValue   JSON or string snapshot of state before the change;
     *                   null for CREATE actions
     * @param newValue   JSON or string snapshot of state after the change;
     *                   null for DELETE actions
     * @param ipAddress  originating IP of the request; null when not available
     */
    void log(
            UUID        actorId,
            String      actorName,
            AuditAction action,
            String      entityType,
            UUID        entityId,
            String      oldValue,
            String      newValue,
            String      ipAddress
    );
}