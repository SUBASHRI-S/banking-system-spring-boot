package com.banksim.audit;

import com.banksim.audit.dto.AuditEventResponse;
import com.banksim.common.PageResponse;
import org.slf4j.MDC;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {

    private static final int MAX_TEXT = 255;

    private final AuditEventRepository events;

    public AuditService(AuditEventRepository events) {
        this.events = events;
    }

    /**
     * Writes in its own transaction, so a FAILED operation (rolled back) still leaves its audit row,
     * and the audit write never holds the business transaction open.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(AuditEventType type, AuditOutcome outcome, Long actorUserId, String target, String detail) {
        events.save(new AuditEvent(type, outcome, actorUserId, truncate(target), truncate(detail), MDC.get("requestId")));
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditEventResponse> list(AuditEventType type, int page, int size) {
        var pageable = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100),
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));
        Page<AuditEvent> result = type == null ? events.findAll(pageable) : events.findByEventType(type, pageable);
        return PageResponse.from(result.map(AuditEventResponse::from));
    }

    private static String truncate(String value) {
        return value == null || value.length() <= MAX_TEXT ? value : value.substring(0, MAX_TEXT);
    }
}
