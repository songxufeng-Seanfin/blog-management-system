-- 只在表不存在时建表：重启不会清空用户自己写的文章
CREATE TABLE IF NOT EXISTS blog_user (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  username      VARCHAR(64)  NOT NULL UNIQUE,
  password_hash VARCHAR(128) NOT NULL,
  nickname      VARCHAR(64)  NOT NULL,
  role          VARCHAR(20)  NOT NULL DEFAULT 'author',
  created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS category (
  id      BIGINT AUTO_INCREMENT PRIMARY KEY,
  name    VARCHAR(64) NOT NULL UNIQUE,
  slug    VARCHAR(64) NOT NULL UNIQUE,
  sort_no INT         NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS article (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  title        VARCHAR(200) NOT NULL,
  summary      VARCHAR(500),
  content      VARCHAR(20000),
  category_id  BIGINT,
  author_id    BIGINT       NOT NULL,
  status       VARCHAR(20)  NOT NULL DEFAULT 'draft',
  view_count   INT          NOT NULL DEFAULT 0,
  created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  published_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS comment (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  article_id  BIGINT      NOT NULL,
  author_name VARCHAR(64) NOT NULL,
  content     VARCHAR(1000) NOT NULL,
  status      VARCHAR(20) NOT NULL DEFAULT 'visible',
  created_at  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS cache_event (
  id         BIGINT AUTO_INCREMENT PRIMARY KEY,
  kind       VARCHAR(20) NOT NULL,
  cache_key  VARCHAR(200) NOT NULL,
  result     VARCHAR(20) NOT NULL,
  detail     VARCHAR(300),
  created_at TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_article_status ON article(status);
CREATE INDEX IF NOT EXISTS idx_comment_article ON comment(article_id);
