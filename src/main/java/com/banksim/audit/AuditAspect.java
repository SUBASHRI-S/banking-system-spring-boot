package com.banksim.audit;

import com.banksim.account.AccountStatus;
import com.banksim.auth.dto.LoginRequest;
import com.banksim.auth.dto.RegisterRequest;
import com.banksim.auth.dto.RegisterResponse;
import com.banksim.common.ApiException;
import java.math.BigDecimal;
import java.util.Locale;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * Audit logging as a cross-cutting concern (AOP): the business services stay free of audit code.
 *
 * The aspect has the highest precedence, so it wraps OUTSIDE the @Transactional proxy of the service:
 * the audit row is written after the business transaction has committed or rolled back, and it
 * reflects what really happened. An audit failure is logged but never breaks the business operation.
 */
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AuditAspect {

    private static final Logger log = LoggerFactory.getLogger(AuditAspect.class);

    private final AuditService audit;

    public AuditAspect(AuditService audit) {
        this.audit = audit;
    }

    @Around("execution(* com.banksim.auth.AuthService.register(..))")
    public Object auditRegister(ProceedingJoinPoint pjp) throws Throwable {
        RegisterRequest request = (RegisterRequest) pjp.getArgs()[0];
        String target = "user:" + normalize(request.email());
        try {
            Object result = pjp.proceed();
            Long userId = result instanceof RegisterResponse response ? response.id() : null;
            safeRecord(AuditEventType.REGISTER, AuditOutcome.SUCCESS, userId, target, null);
            return result;
        } catch (ApiException e) {
            safeRecord(AuditEventType.REGISTER, AuditOutcome.FAILURE, null, target, e.getCode());
            throw e;
        }
    }

    @Around("execution(* com.banksim.auth.AuthService.login(..))")
    public Object auditLogin(ProceedingJoinPoint pjp) throws Throwable {
        LoginRequest request = (LoginRequest) pjp.getArgs()[0];
        String target = "user:" + normalize(request.email());
        try {
            Object result = pjp.proceed();
            safeRecord(AuditEventType.LOGIN_SUCCESS, AuditOutcome.SUCCESS, null, target, null);
            return result;
        } catch (ApiException e) {
            safeRecord(AuditEventType.LOGIN_FAILURE, AuditOutcome.FAILURE, null, target, e.getCode());
            throw e;
        }
    }

    /** Arguments of TransferService.transfer: userId, fromAccountId, toAccountNumber, amount, description, key. */
    @Around("execution(* com.banksim.transfer.TransferService.transfer(..))")
    public Object auditTransfer(ProceedingJoinPoint pjp) throws Throwable {
        Object[] args = pjp.getArgs();
        Long userId = (Long) args[0];
        String target = "account:" + args[1] + "->" + args[2];
        String amount = "amount=" + (args[3] instanceof BigDecimal value ? value.toPlainString() : args[3]);
        try {
            Object result = pjp.proceed();
            safeRecord(AuditEventType.TRANSFER_COMPLETED, AuditOutcome.SUCCESS, userId, target, amount);
            return result;
        } catch (ApiException e) {
            safeRecord(AuditEventType.TRANSFER_REJECTED, AuditOutcome.FAILURE, userId, target,
                    e.getCode() + " " + amount);
            throw e;
        }
    }

    /** Arguments of AccountService.setStatus: accountId, status. The actor is the admin in the security context. */
    @Around("execution(* com.banksim.account.AccountService.setStatus(..))")
    public Object auditStatusChange(ProceedingJoinPoint pjp) throws Throwable {
        Object[] args = pjp.getArgs();
        String target = "account:" + args[0];
        String detail = "status=" + (args[1] instanceof AccountStatus status ? status.name() : args[1]);
        try {
            Object result = pjp.proceed();
            safeRecord(AuditEventType.ACCOUNT_STATUS_CHANGED, AuditOutcome.SUCCESS, currentUserId(), target, detail);
            return result;
        } catch (ApiException e) {
            safeRecord(AuditEventType.ACCOUNT_STATUS_CHANGED, AuditOutcome.FAILURE, currentUserId(), target,
                    e.getCode() + " " + detail);
            throw e;
        }
    }

    private void safeRecord(AuditEventType type, AuditOutcome outcome, Long actorUserId, String target, String detail) {
        try {
            audit.record(type, outcome, actorUserId, target, detail);
        } catch (RuntimeException e) {
            log.error("Could not write audit event {} for {}", type, target, e);
        }
    }

    private static Long currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken token) {
            try {
                return Long.valueOf(token.getToken().getSubject());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private static String normalize(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
