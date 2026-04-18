USE h_user_db;

ALTER TABLE t_user
ADD COLUMN IF NOT EXISTS login_enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT '1=login allowed, 0=login disabled';

