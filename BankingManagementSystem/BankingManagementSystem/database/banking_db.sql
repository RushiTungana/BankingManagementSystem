CREATE DATABASE IF NOT EXISTS banking_db;
USE banking_db;

CREATE TABLE IF NOT EXISTS accounts (
    account_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    full_name VARCHAR(100) NOT NULL,
    email VARCHAR(150) NOT NULL UNIQUE,
    account_type ENUM('SAVINGS', 'CURRENT') NOT NULL DEFAULT 'SAVINGS',
    balance DECIMAL(15,2) NOT NULL DEFAULT 0.00,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_balance_nonnegative CHECK (balance >= 0)
);

CREATE TABLE IF NOT EXISTS transactions (
    transaction_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    account_id BIGINT NOT NULL,
    transaction_type ENUM('DEPOSIT', 'WITHDRAWAL') NOT NULL,
    amount DECIMAL(15,2) NOT NULL,
    balance_after DECIMAL(15,2) NOT NULL,
    description VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_transaction_amount CHECK (amount > 0),
    CONSTRAINT fk_transaction_account FOREIGN KEY (account_id)
      REFERENCES accounts(account_id) ON DELETE RESTRICT
);

CREATE INDEX idx_transactions_account_date ON transactions(account_id, created_at);
