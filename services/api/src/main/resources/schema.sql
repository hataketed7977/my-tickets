CREATE TABLE IF NOT EXISTS app_user (
  id VARCHAR(100) PRIMARY KEY,
  feishu_open_id VARCHAR(128) NOT NULL UNIQUE,
  name VARCHAR(200) NOT NULL,
  avatar_url VARCHAR(2000)
);

CREATE TABLE IF NOT EXISTS user_session (
  id_hash VARCHAR(64) PRIMARY KEY,
  user_id VARCHAR(100) NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
  expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
  oauth_state VARCHAR(128)
);

CREATE TABLE IF NOT EXISTS issue_category (
  id VARCHAR(36) PRIMARY KEY,
  name VARCHAR(100) NOT NULL UNIQUE,
  description VARCHAR(500) NOT NULL DEFAULT '',
  is_active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS ticket (
  id VARCHAR(36) PRIMARY KEY,
  ticket_no VARCHAR(32) NOT NULL UNIQUE,
  title VARCHAR(160) NOT NULL,
  description VARCHAR(5000) NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'open'
    CHECK (status IN ('open', 'in_progress', 'resolved', 'closed')),
  priority VARCHAR(20) NOT NULL DEFAULT 'medium'
    CHECK (priority IN ('low', 'medium', 'high', 'urgent')),
  category_id VARCHAR(36) NOT NULL REFERENCES issue_category(id),
  reporter_user_id VARCHAR(100) NOT NULL REFERENCES app_user(id),
  assignee_user_id VARCHAR(100) REFERENCES app_user(id) ON DELETE SET NULL,
  resolved_at TIMESTAMP WITH TIME ZONE,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_session_expiry ON user_session(expires_at);
CREATE UNIQUE INDEX IF NOT EXISTS idx_session_oauth_state
  ON user_session(oauth_state);
CREATE INDEX IF NOT EXISTS idx_ticket_status_updated
  ON ticket(status, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_ticket_category ON ticket(category_id);
CREATE INDEX IF NOT EXISTS idx_ticket_assignee ON ticket(assignee_user_id);
CREATE INDEX IF NOT EXISTS idx_ticket_reporter ON ticket(reporter_user_id);
