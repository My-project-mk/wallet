-- Wallet creation is idempotent: a retried create request returns the wallet it already made.
-- Treasury wallets keep a NULL key; UNIQUE allows any number of NULLs.
ALTER TABLE wallets ADD COLUMN idempotency_key UUID UNIQUE;
