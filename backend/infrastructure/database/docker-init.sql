-- Local / Docker Compose bootstrap script creating both service databases
CREATE DATABASE IF NOT EXISTS globalrfq_identity CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS global_rfq CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE USER IF NOT EXISTS 'globalrfq_app'@'%' IDENTIFIED BY 'globalrfq_dev_password';
GRANT ALL PRIVILEGES ON globalrfq_identity.* TO 'globalrfq_app'@'%';
GRANT ALL PRIVILEGES ON global_rfq.* TO 'globalrfq_app'@'%';
FLUSH PRIVILEGES;
