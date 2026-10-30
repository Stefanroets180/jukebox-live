ALTER TABLE parties ADD COLUMN credits_valid_days INTEGER NOT NULL DEFAULT 30;
ALTER TABLE parties ADD CONSTRAINT parties_credits_valid_days_range CHECK (credits_valid_days BETWEEN 1 AND 100);

ALTER TABLE credit_transactions ADD COLUMN remaining_credits INTEGER;
ALTER TABLE credit_transactions ADD COLUMN expires_at TIMESTAMP;
ALTER TABLE credit_transactions ADD COLUMN grant_id UUID REFERENCES credit_transactions(id);

UPDATE credit_transactions
SET remaining_credits = CASE WHEN type = 'GRANT' THEN amount ELSE 0 END,
    expires_at = CASE WHEN type = 'GRANT' THEN created_at + INTERVAL '30 days' ELSE NULL END
WHERE remaining_credits IS NULL;

CREATE INDEX idx_credit_grants_available
    ON credit_transactions(user_id, expires_at, created_at, id)
    WHERE type = 'GRANT' AND remaining_credits > 0;
