CREATE TABLE users (
  id BIGINT NOT NULL AUTO_INCREMENT,
  email VARCHAR(255) NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  username VARCHAR(255) NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT uk_users_email UNIQUE (email),
  CONSTRAINT uk_users_username UNIQUE (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE topics (
  id BIGINT NOT NULL AUTO_INCREMENT,
  description VARCHAR(500) NOT NULL,
  name VARCHAR(255) NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT uk_topics_name UNIQUE (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE posts (
  id BIGINT NOT NULL AUTO_INCREMENT,
  content LONGTEXT NOT NULL,
  created_at DATETIME(6) NOT NULL,
  title VARCHAR(255) NOT NULL,
  topic_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT fk_posts_topic FOREIGN KEY (topic_id) REFERENCES topics (id),
  CONSTRAINT fk_posts_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE subscriptions (
  id BIGINT NOT NULL AUTO_INCREMENT,
  topic_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT UniqueUserAndTopic UNIQUE (user_id, topic_id),
  CONSTRAINT fk_subscriptions_topic FOREIGN KEY (topic_id) REFERENCES topics (id),
  CONSTRAINT fk_subscriptions_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE comments (
  id BIGINT NOT NULL AUTO_INCREMENT,
  content VARCHAR(1000) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  post_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT fk_comments_post FOREIGN KEY (post_id) REFERENCES posts (id),
  CONSTRAINT fk_comments_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
