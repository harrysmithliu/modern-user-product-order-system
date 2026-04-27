package com.example.orders.repository;

import com.example.orders.entity.OrderEntity;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<OrderEntity, Long> {

    Optional<OrderEntity> findByUserIdAndRequestNo(Long userId, String requestNo);

    Optional<OrderEntity> findByIdAndUserId(Long id, Long userId);

    Page<OrderEntity> findByUserId(Long userId, Pageable pageable);

    Page<OrderEntity> findByStatus(Integer status, Pageable pageable);

    List<OrderEntity> findByStatusAndExpectedDeliveryTimeLessThanEqual(
            Integer status,
            LocalDateTime expectedDeliveryTime,
            Pageable pageable
    );
}
