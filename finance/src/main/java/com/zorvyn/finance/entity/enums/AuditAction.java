package com.zorvyn.finance.entity.enums;

public enum AuditAction {

    // Financial record lifecycle
    RECORD_CREATED,
    RECORD_UPDATED,
    RECORD_DELETED,

    // User lifecycle
    USER_REGISTERED,
    USER_LOGIN,
    ROLE_CHANGED,
    STATUS_CHANGED
}

