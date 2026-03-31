package com.example.orders.repository;

import com.example.orders.entity.OrderOutboxEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderOutboxRepository extends JpaRepository<OrderOutboxEntity, Long> {

    List<OrderOutboxEntity> findTop50ByStatusOrderByIdAsc(String status);
}
