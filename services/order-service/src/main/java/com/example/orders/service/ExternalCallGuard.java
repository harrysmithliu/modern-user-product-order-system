package com.example.orders.service;

import com.example.orders.config.WorkflowProperties;
import com.example.orders.exception.BusinessException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class ExternalCallGuard {

    private final Executor workflowExecutor;
    private final Semaphore semaphore;
    private final WorkflowProperties properties;

    private final AtomicLong submittedCalls = new AtomicLong();
    private final AtomicLong succeededCalls = new AtomicLong();
    private final AtomicLong failedCalls = new AtomicLong();
    private final AtomicLong rejectedCalls = new AtomicLong();

    public ExternalCallGuard(
            @Qualifier("orderWorkflowExecutor") Executor workflowExecutor,
            @Qualifier("orderExternalCallSemaphore") Semaphore semaphore,
            WorkflowProperties properties
    ) {
        this.workflowExecutor = workflowExecutor;
        this.semaphore = semaphore;
        this.properties = properties;
    }

    public <T> T callSync(String operation, Supplier<T> supplier) {
        submittedCalls.incrementAndGet();
        acquirePermit(operation);
        try {
            T result = CompletableFuture.supplyAsync(supplier, workflowExecutor).join();
            succeededCalls.incrementAndGet();
            return result;
        } catch (CompletionException ex) {
            failedCalls.incrementAndGet();
            throw unwrap(ex);
        } catch (RuntimeException ex) {
            failedCalls.incrementAndGet();
            throw ex;
        } finally {
            semaphore.release();
        }
    }

    public void callAsync(String operation, Runnable runnable, Consumer<RuntimeException> onFailure) {
        submittedCalls.incrementAndGet();
        CompletableFuture.runAsync(() -> {
            acquirePermit(operation);
            try {
                runnable.run();
                succeededCalls.incrementAndGet();
            } catch (RuntimeException ex) {
                failedCalls.incrementAndGet();
                throw ex;
            } finally {
                semaphore.release();
            }
        }, workflowExecutor).whenComplete((unused, throwable) -> {
            if (throwable == null || onFailure == null) {
                return;
            }
            RuntimeException root = unwrap(throwable);
            onFailure.accept(root);
        });
    }

    public long submittedCalls() {
        return submittedCalls.get();
    }

    public long succeededCalls() {
        return succeededCalls.get();
    }

    public long failedCalls() {
        return failedCalls.get();
    }

    public long rejectedCalls() {
        return rejectedCalls.get();
    }

    private void acquirePermit(String operation) {
        try {
            boolean acquired = semaphore.tryAcquire(
                    Math.max(properties.semaphoreAcquireTimeoutMs(), 1),
                    TimeUnit.MILLISECONDS
            );
            if (!acquired) {
                rejectedCalls.incrementAndGet();
                throw new BusinessException(
                        HttpStatus.TOO_MANY_REQUESTS,
                        "Too many concurrent external calls for operation=" + operation
                );
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "Interrupted while waiting external call permit");
        }
    }

    private RuntimeException unwrap(Throwable throwable) {
        Throwable current = throwable;
        while (current instanceof CompletionException && current.getCause() != null) {
            current = current.getCause();
        }
        if (current instanceof RuntimeException runtimeException) {
            return runtimeException;
        }
        if (current instanceof TimeoutException timeoutException) {
            return new BusinessException(HttpStatus.GATEWAY_TIMEOUT, timeoutException.getMessage());
        }
        return new RuntimeException(current);
    }
}
