CREATE TABLE IF NOT EXISTS news_article (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  naver_link_hash CHAR(64) NOT NULL UNIQUE,
  title VARCHAR(500) NOT NULL,
  summary TEXT NULL,
  press VARCHAR(100) NULL,
  published_at DATETIME NULL,
  fetched_at DATETIME NOT NULL,
  raw_json JSON NULL,
  INDEX idx_published_at (published_at DESC)
);

CREATE TABLE IF NOT EXISTS delivery_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  article_id BIGINT NOT NULL,
  channel ENUM('BREAKING','EXCLUSIVE','DEV') NOT NULL,
  status ENUM('SUCCESS','RETRY','FAILED') NOT NULL,
  http_status INT NULL,
  sent_at DATETIME NOT NULL,
  response_body TEXT NULL,
  UNIQUE KEY uniq_article_channel (article_id, channel),
  INDEX idx_sent_at (sent_at DESC)
);

CREATE TABLE IF NOT EXISTS keyword_exclusion (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  scope ENUM('BREAKING','EXCLUSIVE','ALL') NOT NULL,
  keyword VARCHAR(200) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uniq_scope_keyword (scope, keyword)
);

CREATE TABLE IF NOT EXISTS press_exclusion (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  press_name VARCHAR(100) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uniq_press (press_name)
);

CREATE TABLE IF NOT EXISTS runtime_state (
  `key` VARCHAR(100) PRIMARY KEY,
  `value` TEXT NOT NULL,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
