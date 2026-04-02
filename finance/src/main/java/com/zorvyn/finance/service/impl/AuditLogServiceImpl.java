package com.zorvyn.finance.service.impl;

import com.zorvyn.finance.entity.AuditLog;
import com.zorvyn.finance.entity.enums.AuditAction;
import com.zorvyn.finance.repository.AuditLogRepository;
import com.zorvyn.finance.service.AuditLogService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Writes immutable audit log entries.
 *
 * The single most important decision in this class is Propagation.REQUIRES_NEW.
 *
 * Without it, this method joins the caller's transaction. If the caller's
 * transaction is later rolled back (e.g. an exception somewhere after the
 * audit write), the audit entry is rolled back too — you lose the trail of
 * what was attempted. With REQUIRES_NEW, the audit write commits in its own
 * independent transaction the moment this method returns, regardless of what
 * the caller's transaction does afterwards.
 *
 * The reverse is also true: if the audit write itself fails, it throws
 * without disturbing the caller's transaction — the business operation
 * does not fail because the audit trail had a momentary problem.
 */
@Service
public class AuditLogServiceImpl implements AuditLogService {

    private final AuditLogRepository auditLogRepository;

    public AuditLogServiceImpl(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(UUID        actorId,
                    String      actorName,
                    AuditAction action,
                    String      entityType,
                    UUID        entityId,
                    String      oldValue,
                    String      newValue,
                    String      ipAddress) {

        AuditLog entry = new AuditLog(
                actorId,
                actorName,
                action,
                entityType,
                entityId,
                oldValue,
                newValue,
                ipAddress
        );

        auditLogRepository.save(entry);
    }
}