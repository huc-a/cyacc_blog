CREATE TABLE login_throttle (
 account_key char(64) PRIMARY KEY,
 failure_count integer NOT NULL CHECK (failure_count BETWEEN 1 AND 5),
 window_started_at timestamptz NOT NULL,
 locked_until timestamptz
);
CREATE INDEX login_throttle_expiry ON login_throttle(window_started_at);
