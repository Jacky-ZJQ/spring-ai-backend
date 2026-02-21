CREATE TABLE IF NOT EXISTS course (
    id INT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(64) NOT NULL,
    edu TINYINT NOT NULL DEFAULT 0 COMMENT '0-无，1-初中，2-高中，3-大专，4-本科以上',
    type VARCHAR(32) NOT NULL COMMENT '编程、非编程',
    price BIGINT NOT NULL DEFAULT 0,
    duration INT NOT NULL DEFAULT 0 COMMENT '学习时长（天）',
    KEY idx_course_type_edu (type, edu)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS school (
    id INT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(64) NOT NULL,
    city VARCHAR(32) NOT NULL,
    KEY idx_school_city (city)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS course_reservation (
    id INT PRIMARY KEY AUTO_INCREMENT,
    course VARCHAR(64) NOT NULL,
    student_name VARCHAR(64) NOT NULL,
    contact_info VARCHAR(64) NOT NULL,
    school VARCHAR(64) NOT NULL,
    remark VARCHAR(255) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO course (name, edu, type, price, duration)
SELECT 'Java全栈开发', 2, '编程', 12999, 120
WHERE NOT EXISTS (SELECT 1 FROM course WHERE name = 'Java全栈开发');

INSERT INTO course (name, edu, type, price, duration)
SELECT '前端工程化', 2, '编程', 8999, 90
WHERE NOT EXISTS (SELECT 1 FROM course WHERE name = '前端工程化');

INSERT INTO course (name, edu, type, price, duration)
SELECT '数据分析实战', 2, '非编程', 6999, 60
WHERE NOT EXISTS (SELECT 1 FROM course WHERE name = '数据分析实战');

INSERT INTO school (name, city)
SELECT '北京校区', '北京'
WHERE NOT EXISTS (SELECT 1 FROM school WHERE name = '北京校区');

INSERT INTO school (name, city)
SELECT '上海校区', '上海'
WHERE NOT EXISTS (SELECT 1 FROM school WHERE name = '上海校区');

INSERT INTO school (name, city)
SELECT '深圳校区', '深圳'
WHERE NOT EXISTS (SELECT 1 FROM school WHERE name = '深圳校区');
