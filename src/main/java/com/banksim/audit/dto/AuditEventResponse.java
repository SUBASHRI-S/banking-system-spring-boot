package com.banksim.audit.dto;

import com.banksim.audit.AuditEvent;
import com.banksim.audit.AuditEventType;
import com.banksim.audit.AuditOutcome;
import java.time.Instant;

public record AuditEventResponse(
        Long id,
        AuditEventType eventType,
        AuditOutcome outcome,
        Long actorUserId,
        String target,
        String detail,
        String requestId,
        Instant createdAt) {

    public static AuditEventResponse from(AuditEvent e) {
        return new AuditEventResponse(e.getId(), e.getEventType(), e.getOutcome(), e.getActorUserId(),
                e.getTarget(), e.getDetail(), e.getRequestId(), e.getCreatedAt());
    }
}
