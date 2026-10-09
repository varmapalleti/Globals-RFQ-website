CREATE DATABASE identity_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'identity_user'@'localhost' IDENTIFIED BY 'root';
GRANT ALL PRIVILEGES ON identity_db.* TO 'identity_user'@'localhost';
FLUSH PRIVILEGES;