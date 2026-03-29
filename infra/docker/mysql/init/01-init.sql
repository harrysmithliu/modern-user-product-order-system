CREATE DATABASE IF NOT EXISTS h_user_db CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS h_product_db CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS h_order_db CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE USER IF NOT EXISTS 'app'@'%' IDENTIFIED BY 'app_pass';
GRANT ALL PRIVILEGES ON h_user_db.* TO 'app'@'%';
GRANT ALL PRIVILEGES ON h_product_db.* TO 'app'@'%';
GRANT ALL PRIVILEGES ON h_order_db.* TO 'app'@'%';
FLUSH PRIVILEGES;

USE h_user_db;

CREATE TABLE IF NOT EXISTS t_user (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'User ID',
  userno VARCHAR(10) NOT NULL COMMENT 'Unique User Identifier',
  username VARCHAR(50) NOT NULL COMMENT 'Username',
  password VARCHAR(100) NOT NULL COMMENT 'Encrypted Password (BCrypt Hash)',
  nickname VARCHAR(50) DEFAULT NULL COMMENT 'Nickname',
  phone VARCHAR(20) DEFAULT NULL COMMENT 'Phone Number',
  email VARCHAR(100) DEFAULT NULL COMMENT 'Email Address',
  role VARCHAR(20) NOT NULL DEFAULT 'USER' COMMENT 'Role: USER / ADMIN',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Creation Time',
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Update Time',
  version INT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY userno (userno),
  UNIQUE KEY uk_username (username),
  KEY idx_phone (phone)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='User Table';

INSERT INTO t_user (userno, username, password, nickname, phone, email, role, version)
VALUES
  ('U0001', 'john_smith', '$2b$10$u3dDBjHhxOmVL13D16hso..mXTxr3g/X.AFX5sbPAzq61t8ELj84G', 'John', '13800000001', 'john.smith@example.com', 'USER', 1),
  ('U0002', 'emily_johnson', '$2b$10$u3dDBjHhxOmVL13D16hso..mXTxr3g/X.AFX5sbPAzq61t8ELj84G', 'Emily', '13800000002', 'emily.johnson@example.com', 'USER', 1),
  ('U0003', 'michael_brown', '$2b$10$u3dDBjHhxOmVL13D16hso..mXTxr3g/X.AFX5sbPAzq61t8ELj84G', 'Michael', '13800000003', 'michael.brown@example.com', 'USER', 1),
  ('U0004', 'sarah_davis', '$2b$10$u3dDBjHhxOmVL13D16hso..mXTxr3g/X.AFX5sbPAzq61t8ELj84G', 'Sarah', '13800000004', 'sarah.davis@example.com', 'USER', 1),
  ('U0005', 'david_miller', '$2b$10$u3dDBjHhxOmVL13D16hso..mXTxr3g/X.AFX5sbPAzq61t8ELj84G', 'David', '13800000005', 'david.miller@example.com', 'USER', 1),
  ('U9999', 'admin', '$2b$10$72sGKhdfKdep1rYAEVfhD.jfwFsX5OzXVfH.mIn.xsE8gErpaPO4a', 'System Admin', '13900000000', 'admin@example.com', 'ADMIN', 0)
ON DUPLICATE KEY UPDATE
  password = VALUES(password),
  nickname = VALUES(nickname),
  phone = VALUES(phone),
  email = VALUES(email),
  role = VALUES(role),
  version = VALUES(version);

USE h_product_db;

CREATE TABLE IF NOT EXISTS t_product (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Product ID',
  product_name VARCHAR(200) NOT NULL COMMENT 'Product Name',
  product_code VARCHAR(50) NOT NULL COMMENT 'Product Code',
  price DECIMAL(10,2) NOT NULL COMMENT 'Price',
  stock INT NOT NULL DEFAULT 0 COMMENT 'Stock Quantity',
  category VARCHAR(50) DEFAULT NULL COMMENT 'Category',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '1=ON_SALE 0=OFF_SALE',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Creation Time',
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Update Time',
  version INT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_product_code (product_code),
  KEY idx_category (category),
  CONSTRAINT chk_product_price_nonnegative CHECK ((price >= 0)),
  CONSTRAINT chk_product_stock_nonnegative CHECK ((stock >= 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Product Table';

INSERT INTO t_product (product_name, product_code, price, stock, category, status, version)
VALUES
  ('Mechanical Keyboard K87', 'P1001', 299.00, 120, 'Accessories', 1, 0),
  ('Wireless Mouse M2', 'P1002', 129.00, 200, 'Accessories', 1, 0),
  ('27-inch 4K Monitor', 'P1003', 1899.00, 35, 'Display', 1, 0),
  ('USB-C Docking Station', 'P1004', 499.00, 60, 'Accessories', 1, 0),
  ('Notebook Pro 14 Sleeve', 'P1005', 89.00, 150, 'Bags', 1, 0),
  ('Ergonomic Office Chair', 'P1006', 1399.00, 20, 'Furniture', 1, 0),
  ('Standing Desk Basic', 'P1007', 2199.00, 15, 'Furniture', 1, 0),
  ('Noise Cancelling Headphones', 'P1008', 999.00, 45, 'Audio', 1, 0)
ON DUPLICATE KEY UPDATE
  product_name = VALUES(product_name),
  price = VALUES(price),
  stock = VALUES(stock),
  category = VALUES(category),
  status = VALUES(status),
  version = VALUES(version);

USE h_order_db;

CREATE TABLE IF NOT EXISTS t_order (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Order ID',
  order_no VARCHAR(32) NOT NULL COMMENT 'Order Number',
  request_no VARCHAR(64) DEFAULT NULL COMMENT 'Idempotency request number',
  user_id BIGINT NOT NULL COMMENT 'User ID',
  product_id BIGINT NOT NULL COMMENT 'Product ID',
  quantity INT NOT NULL COMMENT 'Quantity',
  total_amount DECIMAL(10,2) NOT NULL COMMENT 'Total Amount',
  status TINYINT NOT NULL DEFAULT 0 COMMENT '0=PENDING_APPROVAL 1=APPROVED 2=REJECTED 3=CANCELLED',
  reject_reason VARCHAR(255) DEFAULT NULL COMMENT 'Reject reason',
  approve_time DATETIME DEFAULT NULL COMMENT 'Approve time',
  cancel_time DATETIME DEFAULT NULL COMMENT 'Cancel time',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Creation Time',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Update Time',
  version INT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_order_no (order_no),
  UNIQUE KEY uk_user_request_no (user_id, request_no),
  KEY idx_user_id (user_id),
  KEY idx_product_id (product_id),
  KEY idx_status (status),
  KEY idx_create_time (create_time),
  KEY idx_user_status_time (user_id, status, create_time),
  KEY idx_total_amount (total_amount),
  KEY idx_create_time_status (create_time, status),
  KEY idx_product_status_time (product_id, status, create_time),
  KEY idx_status_create_time (status, create_time),
  CONSTRAINT chk_order_quantity_positive CHECK ((quantity > 0)),
  CONSTRAINT chk_order_total_amount_nonnegative CHECK ((total_amount >= 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Order Table';

CREATE TABLE IF NOT EXISTS t_message_consume_log (
  id BIGINT NOT NULL AUTO_INCREMENT,
  message_id VARCHAR(64) NOT NULL COMMENT 'Unique message id',
  consumer_name VARCHAR(100) NOT NULL COMMENT 'Consumer name',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_message_consumer (message_id, consumer_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='MQ consume dedup log';
