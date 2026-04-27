package com.example.orders.repository;

import com.example.orders.entity.OrderCouponIssueTaskEntity;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderCouponIssueTaskRepository extends JpaRepository<OrderCouponIssueTaskEntity, Long> {

    Optional<OrderCouponIssueTaskEntity> findByOrderNo(String orderNo);

    List<OrderCouponIssueTaskEntity> findTop50ByStatusAndNextRetryTimeLessThanEqualOrderByIdAsc(
            String status,
            LocalDateTime nextRetryTime
    );
}
