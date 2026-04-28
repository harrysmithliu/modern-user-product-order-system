package com.example.orders.service;

import com.example.orders.config.WorkflowProperties;
import com.example.orders.entity.OrderEntity;
import com.example.orders.enums.OrderStatus;
import com.example.orders.repository.OrderRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class OrderAutoCompleteService {

    private static final Logger log = LoggerFactory.getLogger(OrderAutoCompleteService.class);
    private final AtomicBoolean running = new AtomicBoolean(false);

    private final OrderRepository orderRepository;
    private final WorkflowProperties workflowProperties;

    public OrderAutoCompleteService(
            OrderRepository orderRepository,
            WorkflowProperties workflowProperties
    ) {
        this.orderRepository = orderRepository;
        this.workflowProperties = workflowProperties;
    }

    @Scheduled(cron = "${app.workflow.order-auto-complete-main-cron:0 15 9 * * MON-FRI}",
            zone = "${app.workflow.order-auto-complete-zone:America/Toronto}")
    public void autoCompleteMainWindow() {
        runAutoComplete("main-window");
    }

    @Scheduled(fixedDelayString = "${app.workflow.order-auto-complete-compensation-fixed-delay-ms:3600000}")
    public void autoCompleteCompensation() {
        runAutoComplete("compensation");
    }

    private void runAutoComplete(String trigger) {
        if (!running.compareAndSet(false, true)) {
            log.info("order auto-complete skipped due to running task trigger={}", trigger);
            return;
        }
        try {
            autoCompleteDeliveredOrders(trigger);
        } finally {
            running.set(false);
        }
    }

    private void autoCompleteDeliveredOrders(String trigger) {
        LocalDateTime now = LocalDateTime.now();
        int batchSize = Math.max(workflowProperties.orderAutoCompleteBatchSize(), 1);

        List<OrderEntity> candidates = orderRepository.findByStatusAndExpectedDeliveryTimeLessThanEqual(
                OrderStatus.SHIPPING.getCode(),
                now,
                PageRequest.of(
                        0,
                        batchSize,
                        Sort.by(
                                Sort.Order.asc("expectedDeliveryTime"),
                                Sort.Order.asc("id")
                        )
                )
        );

        if (!candidates.isEmpty()) {
            log.info("order auto-complete triggered trigger={} candidate_count={}", trigger, candidates.size());
        }

        for (OrderEntity candidate : candidates) {
            markCompletedIfDue(candidate.getId(), now);
        }
    }

    private void markCompletedIfDue(Long orderId, LocalDateTime now) {
        try {
            orderRepository.findById(orderId).ifPresent(order -> {
                if (OrderStatus.SHIPPING.getCode() != order.getStatus()) {
                    return;
                }
                if (order.getExpectedDeliveryTime() == null || order.getExpectedDeliveryTime().isAfter(now)) {
                    return;
                }
                if (order.getCompleteTime() != null) {
                    return;
                }
                order.setStatus(OrderStatus.COMPLETED.getCode());
                order.setCompleteTime(now);
                orderRepository.save(order);
            });
        } catch (ObjectOptimisticLockingFailureException ex) {
            log.info("order auto-complete skipped due to concurrent update order_id={}", orderId);
        } catch (RuntimeException ex) {
            log.warn("order auto-complete failed order_id={} error={}", orderId, ex.getMessage());
        }
    }
}
