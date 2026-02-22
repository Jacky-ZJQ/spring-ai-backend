-- ----------------------------
-- 1. 课程表 (星巴克咖啡课程)
-- ----------------------------
CREATE TABLE IF NOT EXISTS course (
                                      id INT PRIMARY KEY AUTO_INCREMENT,
                                      name VARCHAR(64) NOT NULL COMMENT '课程名称，例如：手冲咖啡入门',
                                      edu TINYINT NOT NULL DEFAULT 0 COMMENT '难度/适合等级：0-无要求，1-初级，2-中级，3-高级，4-大师',
                                      type VARCHAR(32) NOT NULL COMMENT '课程分类：咖啡技艺、门店管理、品鉴认证等',
                                      price BIGINT NOT NULL DEFAULT 0 COMMENT '价格（分）',
                                      duration INT NOT NULL DEFAULT 0 COMMENT '学习时长（天）',
                                      KEY idx_course_type_edu (type, edu)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------
-- 2. 校区表 (星巴克门店)
-- ----------------------------
CREATE TABLE IF NOT EXISTS school (
                                      id INT PRIMARY KEY AUTO_INCREMENT,
                                      name VARCHAR(64) NOT NULL COMMENT '门店名称',
                                      city VARCHAR(32) NOT NULL COMMENT '所在城市',
                                      KEY idx_school_city (city)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------
-- 3. 课程预约表
-- ----------------------------
CREATE TABLE IF NOT EXISTS course_reservation (
                                                  id INT PRIMARY KEY AUTO_INCREMENT,
                                                  course VARCHAR(64) NOT NULL COMMENT '预约的课程名称',
                                                  student_name VARCHAR(64) NOT NULL COMMENT '学员姓名',
                                                  contact_info VARCHAR(64) NOT NULL COMMENT '联系方式（手机/微信）',
                                                  school VARCHAR(64) NOT NULL COMMENT '预约门店名称',
                                                  remark VARCHAR(255) NULL COMMENT '备注',
                                                  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '预约时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ========== 兼容历史库：若缺少 created_at 字段则补齐 ==========
SET @has_created_at := (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'course_reservation'
      AND column_name = 'created_at'
);

SET @ddl := IF(
    @has_created_at = 0,
    'ALTER TABLE course_reservation ADD COLUMN created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT ''预约时间''',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
-- =================================================

-- ========== 新增：清空原有数据（按依赖顺序） ==========
-- 注意：仅建议在初始化环境执行。若用于已有生产库，请先移除 TRUNCATE 段再执行。
TRUNCATE TABLE course_reservation;  -- 先清空预约表（因为它引用了课程和门店的名称）
TRUNCATE TABLE course;              -- 再清空课程表
TRUNCATE TABLE school;              -- 最后清空门店表
-- =================================================

-- ----------------------------
-- 4. 插入星巴克咖啡课程数据
-- ----------------------------
INSERT INTO course (name, edu, type, price, duration)
SELECT '手冲咖啡入门', 1, '咖啡技艺', 59900, 1
WHERE NOT EXISTS (SELECT 1 FROM course WHERE name = '手冲咖啡入门');

INSERT INTO course (name, edu, type, price, duration)
SELECT '意式萃取与拉花基础', 2, '咖啡技艺', 89900, 2
WHERE NOT EXISTS (SELECT 1 FROM course WHERE name = '意式萃取与拉花基础');

INSERT INTO course (name, edu, type, price, duration)
SELECT '咖啡品鉴与杯测', 2, '品鉴认证', 129900, 1
WHERE NOT EXISTS (SELECT 1 FROM course WHERE name = '咖啡品鉴与杯测');

INSERT INTO course (name, edu, type, price, duration)
SELECT '门店管理实战', 3, '门店管理', 299900, 5
WHERE NOT EXISTS (SELECT 1 FROM course WHERE name = '门店管理实战');

INSERT INTO course (name, edu, type, price, duration)
SELECT '烘焙工坊深度体验', 0, '文化沙龙', 39900, 1
WHERE NOT EXISTS (SELECT 1 FROM course WHERE name = '烘焙工坊深度体验');

-- ----------------------------
-- 5. 插入星巴克校区（门店）数据
-- ----------------------------
INSERT INTO school (name, city)
SELECT '北京坊甄选店', '北京'
WHERE NOT EXISTS (SELECT 1 FROM school WHERE name = '北京坊甄选店');

INSERT INTO school (name, city)
SELECT '上海烘焙工坊', '上海'
WHERE NOT EXISTS (SELECT 1 FROM school WHERE name = '上海烘焙工坊');

INSERT INTO school (name, city)
SELECT '深圳湾万象城店', '深圳'
WHERE NOT EXISTS (SELECT 1 FROM school WHERE name = '深圳湾万象城店');

-- ----------------------------
-- 6. 插入预约记录示例
-- ----------------------------
INSERT INTO course_reservation (course, student_name, contact_info, school, remark)
SELECT '手冲咖啡入门', '张三', '13800138001', '北京坊甄选店', '希望上午上课'
WHERE NOT EXISTS (SELECT 1 FROM course_reservation WHERE student_name = '张三' AND course = '手冲咖啡入门');

INSERT INTO course_reservation (course, student_name, contact_info, school, remark)
SELECT '意式萃取与拉花基础', '李四', '13800138002', '上海烘焙工坊', '零基础'
WHERE NOT EXISTS (SELECT 1 FROM course_reservation WHERE student_name = '李四' AND course = '意式萃取与拉花基础');

INSERT INTO course_reservation (course, student_name, contact_info, school, remark)
SELECT '咖啡品鉴与杯测', '王五', '13800138003', '深圳湾万象城店', NULL
WHERE NOT EXISTS (SELECT 1 FROM course_reservation WHERE student_name = '王五' AND course = '咖啡品鉴与杯测');
