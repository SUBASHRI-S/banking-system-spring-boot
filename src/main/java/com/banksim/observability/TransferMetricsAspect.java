package com.banksim.observability;

import com.banksim.common.ApiException;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Business metrics for transfers (BANK-NFR-08): how many succeed, how many are rejected and why, and how long
 * they take. Every counter uses the same two tag keys (outcome, reason), which Prometheus requires.
 */
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class TransferMetricsAspect {

    private final MeterRegistry registry;

    public TransferMetricsAspect(MeterRegistry registry) {
        this.registry = registry;
    }

    @Around("execution(* com.banksim.transfer.TransferService.transfer(..))")
    public Object measure(ProceedingJoinPoint pjp) throws Throwable {
        Timer.Sample sample = Timer.start(registry);
        try {
            Object result = pjp.proceed();
            registry.counter("banksim.transfers", "outcome", "completed", "reason", "none").increment();
            return result;
        } catch (ApiException e) {
            registry.counter("banksim.transfers", "outcome", "rejected", "reason", e.getCode()).increment();
            throw e;
        } finally {
            sample.stop(Timer.builder("banksim.transfer.duration")
                    .description("Time spent executing a transfer, including lock waits")
                    .register(registry));
        }
    }
}
