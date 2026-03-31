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
