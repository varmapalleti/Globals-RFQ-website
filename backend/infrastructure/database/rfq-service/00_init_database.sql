-- Run this script as a MySQL administrator during local/development setup.
-- Replace BOTH password placeholders with the same strong, unique password.
-- Set that same value as RFQ_DB_PASSWORD in the RFQ Service run configuration.
-- Do not commit the real password or use the MySQL root account for the service.

CREATE DATABASE IF NOT EXISTS global_rfq
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

CREATE USER IF NOT EXISTS 'global_rfq_app'@'localhost'
    IDENTIFIED BY 'root';

-- Ensures rerunning this setup script updates the dedicated account password.
ALTER USER 'global_rfq_app'@'localhost'
    IDENTIFIED BY 'root';

-- RFQ Service uses Flyway at startup, so this account needs schema migration
-- privileges, as well as normal application read/write privileges, on this DB.
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, REFERENCES
    ON global_rfq.* TO 'global_rfq_app'@'localhost';
