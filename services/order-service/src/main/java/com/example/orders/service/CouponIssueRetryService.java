package com.example.orders.service;

import com.example.orders.config.CouponPlatformProperties;
import com.example.orders.config.WorkflowProperties;
import com.example.orders.entity.OrderCouponIssueTaskEntity;
import com.example.orders.repository.OrderCouponIssueTaskRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CouponIssueRetryService {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILED = "FAILED";

    private static final Logger log = LoggerFactory.getLogger(CouponIssueRetryService.class);

    private final OrderCouponIssueTaskRepository taskRepository;
    private final CouponPlatformClient couponPlatformClient;
    private final ExternalCallGuard externalCallGuard;
    private final CouponPlatformProperties couponPlatformProperties;
    private final WorkflowProperties workflowProperties;

    public CouponIssueRetryService(
            OrderCouponIssueTaskRepository taskRepository,
            CouponPlatformClient couponPlatformClient,
            ExternalCallGuard externalCallGuard,
            CouponPlatformProperties couponPlatformProperties,
            WorkflowProperties workflowProperties
    ) {
        this.taskRepository = taskRepository;
        this.couponPlatformClient = couponPlatformClient;
        this.externalCallGuard = externalCallGuard;
        this.couponPlatformProperties = couponPlatformProperties;
        this.workflowProperties = workflowProperties;
    }

    @Transactional
    public void scheduleRetry(Long orderId, String orderNo, Long userId, BigDecimal orderAmount, String errorMessage) {
        if (!couponPlatformProperties.enabled()) {
            return;
        }
        if (orderNo == null || orderNo.isBlank()) {
            return;
        }
        OrderCouponIssueTaskEntity task = taskRepository.findByOrderNo(orderNo).orElseGet(OrderCouponIssueTaskEntity::new);
        if (task.getId() == null) {
            task.setOrderId(orderId);
            task.setOrderNo(orderNo);
            task.setUserId(userId);
            task.setOrderAmount(orderAmount);
            task.setRetryCount(0);
        }
        if (STATUS_SUCCESS.equals(task.getStatus()) || STATUS_FAILED.equals(task.getStatus())) {
            return;
        }
        task.setStatus(STATUS_PENDING);
        task.setNextRetryTime(LocalDateTime.now().plusSeconds(Math.max(workflowProperties.couponIssueRetryDelaySeconds(), 1)));
        task.setLastError(shorten(errorMessage));
        taskRepository.save(task);
    }

    @Scheduled(fixedDelayString = "${app.workflow.coupon-issue-retry-fixed-delay-ms:3000}")
    public void retryPendingTasks() {
        if (!couponPlatformProperties.enabled()) {
            return;
        }
        List<OrderCouponIssueTaskEntity> tasks = taskRepository.findTop50ByStatusAndNextRetryTimeLessThanEqualOrderByIdAsc(
                STATUS_PENDING,
                LocalDateTime.now()
        );
        for (OrderCouponIssueTaskEntity task : tasks) {
            processRetry(task);
        }
    }

    private void processRetry(OrderCouponIssueTaskEntity task) {
        try {
            externalCallGuard.callSync(
                    "coupon-issue-retry",
                    () -> couponPlatformClient.issueCoupon(task.getUserId(), task.getOrderAmount(), task.getOrderNo())
            );
            markSuccess(task.getId());
            log.info("coupon issue retry success order_no={} task_id={}", task.getOrderNo(), task.getId());
        } catch (RuntimeException ex) {
            markFailure(task.getId(), ex.getMessage());
            log.warn("coupon issue retry failed order_no={} task_id={} error={}", task.getOrderNo(), task.getId(), ex.getMessage());
        }
    }

    @Transactional
    protected void markSuccess(Long taskId) {
        taskRepository.findById(taskId).ifPresent(task -> {
            task.setStatus(STATUS_SUCCESS);
            task.setLastError(null);
            taskRepository.save(task);
        });
    }

    @Transactional
    protected void markFailure(Long taskId, String errorMessage) {
        taskRepository.findById(taskId).ifPresent(task -> {
            int nextRetryCount = task.getRetryCount() + 1;
            task.setRetryCount(nextRetryCount);
            task.setLastError(shorten(errorMessage));
            if (nextRetryCount >= Math.max(workflowProperties.couponIssueMaxRetries(), 1)) {
                task.setStatus(STATUS_FAILED);
                task.setNextRetryTime(LocalDateTime.now());
            } else {
                task.setStatus(STATUS_PENDING);
                task.setNextRetryTime(LocalDateTime.now().plusSeconds(Math.max(workflowProperties.couponIssueRetryDelaySeconds(), 1)));
            }
            taskRepository.save(task);
        });
    }

    private String shorten(String message) {
        if (message == null || message.isBlank()) {
            return "unknown error";
        }
        if (message.length() > 500) {
            return message.substring(0, 500);
        }
        return message;
    }
}
