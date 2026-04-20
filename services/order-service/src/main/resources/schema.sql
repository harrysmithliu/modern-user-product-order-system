SET @ddl = (
  SELECT IF(
    EXISTS (SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_order')
    AND NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_order' AND COLUMN_NAME = 'origin_amount'),
    'ALTER TABLE t_order ADD COLUMN origin_amount DECIMAL(10,2) DEFAULT NULL COMMENT ''Original order amount before discount'' AFTER total_amount',
    'SELECT 1'
  )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
  SELECT IF(
    EXISTS (SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_order')
    AND NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_order' AND COLUMN_NAME = 'discount_amount'),
    'ALTER TABLE t_order ADD COLUMN discount_amount DECIMAL(10,2) DEFAULT NULL COMMENT ''Discount amount from coupon'' AFTER origin_amount',
    'SELECT 1'
  )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
  SELECT IF(
    EXISTS (SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_order')
    AND NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_order' AND COLUMN_NAME = 'final_amount'),
    'ALTER TABLE t_order ADD COLUMN final_amount DECIMAL(10,2) DEFAULT NULL COMMENT ''Final payable amount after discount'' AFTER discount_amount',
    'SELECT 1'
  )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
  SELECT IF(
    EXISTS (SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_order')
    AND NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_order' AND COLUMN_NAME = 'payment_time'),
    'ALTER TABLE t_order ADD COLUMN payment_time DATETIME DEFAULT NULL COMMENT ''Payment completion time'' AFTER cancel_time',
    'SELECT 1'
  )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
  SELECT IF(
    EXISTS (SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_order')
    AND NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_order' AND COLUMN_NAME = 'ship_time'),
    'ALTER TABLE t_order ADD COLUMN ship_time DATETIME DEFAULT NULL COMMENT ''Shipping start time'' AFTER payment_time',
    'SELECT 1'
  )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
  SELECT IF(
    EXISTS (SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_order')
    AND NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_order' AND COLUMN_NAME = 'expected_delivery_time'),
    'ALTER TABLE t_order ADD COLUMN expected_delivery_time DATETIME DEFAULT NULL COMMENT ''Expected delivery time'' AFTER ship_time',
    'SELECT 1'
  )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
  SELECT IF(
    EXISTS (SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_order')
    AND NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_order' AND COLUMN_NAME = 'complete_time'),
    'ALTER TABLE t_order ADD COLUMN complete_time DATETIME DEFAULT NULL COMMENT ''Order completion time'' AFTER expected_delivery_time',
    'SELECT 1'
  )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
  SELECT IF(
    EXISTS (SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_order')
    AND NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_order' AND COLUMN_NAME = 'refund_time'),
    'ALTER TABLE t_order ADD COLUMN refund_time DATETIME DEFAULT NULL COMMENT ''Refund completion time'' AFTER complete_time',
    'SELECT 1'
  )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
  SELECT IF(
    EXISTS (SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_order')
    AND NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_order' AND INDEX_NAME = 'idx_status_expected_delivery_id'),
    'ALTER TABLE t_order ADD KEY idx_status_expected_delivery_id (status, expected_delivery_time, id)',
    'SELECT 1'
  )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS t_order_outbox (
  id BIGINT NOT NULL AUTO_INCREMENT,
  message_id VARCHAR(64) NOT NULL COMMENT 'Unique event message id',
  routing_key VARCHAR(100) NOT NULL COMMENT 'RabbitMQ routing key',
  payload_json LONGTEXT NOT NULL COMMENT 'Serialized event payload',
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING / PUBLISHED',
  attempt_count INT NOT NULL DEFAULT 0 COMMENT 'Publish attempt count',
  published_at DATETIME DEFAULT NULL COMMENT 'Publish time',
  last_error VARCHAR(500) DEFAULT NULL COMMENT 'Last publish error',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_message_id (message_id),
  KEY idx_status_id (status, id),
  KEY idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Order outbox table';

CREATE TABLE IF NOT EXISTS t_order_coupon_issue_task (
  id BIGINT NOT NULL AUTO_INCREMENT,
  order_id BIGINT NOT NULL COMMENT 'Order id',
  order_no VARCHAR(64) NOT NULL COMMENT 'Order number',
  user_id BIGINT NOT NULL COMMENT 'User id',
  order_amount DECIMAL(10,2) NOT NULL COMMENT 'Order amount for coupon issue',
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING / SUCCESS / FAILED',
  retry_count INT NOT NULL DEFAULT 0 COMMENT 'Retry count',
  next_retry_time DATETIME NOT NULL COMMENT 'Next retry time',
  last_error VARCHAR(500) DEFAULT NULL COMMENT 'Last error',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_order_no (order_no),
  KEY idx_status_retry_time (status, next_retry_time),
  KEY idx_order_id (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Coupon issue retry tasks';
