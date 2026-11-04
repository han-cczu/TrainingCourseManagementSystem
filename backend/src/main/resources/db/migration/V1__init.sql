-- 培训课程管理系统 · 初始表结构（技术设计 §5.1）

CREATE TABLE sys_user (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  username      VARCHAR(32)  NOT NULL COMMENT '登录名，字母/数字/下划线',
  password_hash VARCHAR(100) NOT NULL COMMENT 'BCrypt 哈希',
  real_name     VARCHAR(32)  NOT NULL COMMENT '姓名',
  department    VARCHAR(64)  NULL     COMMENT '部门',
  role          VARCHAR(16)  NOT NULL COMMENT 'ADMIN / EMPLOYEE',
  enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
  created_at    DATETIME     NOT NULL,
  updated_at    DATETIME     NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_username (username)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '用户';

CREATE TABLE course (
  id             BIGINT       NOT NULL AUTO_INCREMENT,
  title          VARCHAR(100) NOT NULL,
  description    TEXT         NULL,
  instructor     VARCHAR(32)  NOT NULL COMMENT '讲师',
  location       VARCHAR(100) NULL     COMMENT '地点/会议链接',
  start_time     DATETIME     NOT NULL,
  end_time       DATETIME     NOT NULL,
  capacity       INT          NULL     COMMENT '容量，NULL 表示不限',
  enrolled_count INT          NOT NULL DEFAULT 0 COMMENT '冗余计数：ENROLLED + COMPLETED',
  created_by     BIGINT       NOT NULL,
  created_at     DATETIME     NOT NULL,
  updated_at     DATETIME     NOT NULL,
  PRIMARY KEY (id),
  KEY idx_course_start_time (start_time),
  CONSTRAINT fk_course_creator FOREIGN KEY (created_by) REFERENCES sys_user (id),
  CONSTRAINT chk_course_time CHECK (end_time > start_time),
  CONSTRAINT chk_course_capacity CHECK (capacity IS NULL OR capacity > 0),
  CONSTRAINT chk_course_count CHECK (enrolled_count >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '课程';

CREATE TABLE enrollment (
  id           BIGINT      NOT NULL AUTO_INCREMENT,
  course_id    BIGINT      NOT NULL,
  user_id      BIGINT      NOT NULL,
  status       VARCHAR(16) NOT NULL COMMENT 'ENROLLED / CANCELLED / COMPLETED',
  enrolled_at  DATETIME    NOT NULL,
  cancelled_at DATETIME    NULL,
  completed_at DATETIME    NULL,
  completed_by BIGINT      NULL COMMENT '= user_id 为本人确认，否则为管理员确认',
  created_at   DATETIME    NOT NULL,
  updated_at   DATETIME    NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_enrollment_course_user (course_id, user_id),
  KEY idx_enrollment_user_status (user_id, status),
  KEY idx_enrollment_course_status (course_id, status),
  CONSTRAINT fk_enrollment_course FOREIGN KEY (course_id) REFERENCES course (id),
  CONSTRAINT fk_enrollment_user FOREIGN KEY (user_id) REFERENCES sys_user (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '报名（课程-用户 多对多）';
