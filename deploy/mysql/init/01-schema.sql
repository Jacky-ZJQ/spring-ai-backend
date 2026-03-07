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

-- ----------------------------
-- 7. MCP 网关服务器配置表
-- ----------------------------
CREATE TABLE IF NOT EXISTS mcp_gateway_server (
                                                   id BIGINT PRIMARY KEY AUTO_INCREMENT,
                                                   server_name VARCHAR(100) NOT NULL COMMENT '服务器名称',
                                                   connection_type VARCHAR(16) NOT NULL COMMENT '连接类型: HTTP/SSE/STDIO',
                                                   connection_url VARCHAR(512) NULL COMMENT 'HTTP/SSE 连接地址',
                                                   sse_endpoint VARCHAR(512) NULL COMMENT 'SSE endpoint',
                                                   stdio_command VARCHAR(256) NULL COMMENT 'STDIO 命令',
                                                   stdio_args_json TEXT NULL COMMENT 'STDIO args JSON',
                                                   stdio_env_json TEXT NULL COMMENT 'STDIO env JSON',
                                                   is_code_client TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否代码模式客户端',
                                                   is_ping_available TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否开启 ping 可用性检查',
                                                   tool_sync_interval_minutes INT NOT NULL DEFAULT 10 COMMENT '工具同步间隔(分钟)',
                                                   headers_json TEXT NULL COMMENT '请求头 JSON',
                                                   auth_type VARCHAR(32) NOT NULL DEFAULT 'none' COMMENT '鉴权类型',
                                                   enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
                                                   created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                                   updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                                                   KEY idx_mcp_gateway_server_name (server_name),
                                                   KEY idx_mcp_gateway_server_type (connection_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------
-- 8. MCP 网关工具策略表
-- ----------------------------
CREATE TABLE IF NOT EXISTS mcp_gateway_tool_policy (
                                                        id BIGINT PRIMARY KEY AUTO_INCREMENT,
                                                        server_id BIGINT NOT NULL COMMENT '所属服务器ID',
                                                        tool_name VARCHAR(128) NOT NULL COMMENT '工具名称',
                                                        tool_description VARCHAR(512) NULL COMMENT '工具描述',
                                                        input_schema_json MEDIUMTEXT NULL COMMENT 'input schema JSON',
                                                        enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
                                                        auto_execute TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否自动执行',
                                                        cost_usd DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '成本(美元)',
                                                        sort_order INT NOT NULL DEFAULT 0 COMMENT '排序',
                                                        last_sync_at TIMESTAMP NULL DEFAULT NULL COMMENT '最近同步时间',
                                                        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                                        updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                                                        UNIQUE KEY uk_mcp_gateway_tool_policy_server_tool (server_id, tool_name),
                                                        KEY idx_mcp_gateway_tool_policy_server_id (server_id),
                                                        CONSTRAINT fk_mcp_gateway_tool_policy_server
                                                            FOREIGN KEY (server_id) REFERENCES mcp_gateway_server(id)
                                                                ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------
-- 9. AI 知识库内容分类表
-- ----------------------------
CREATE TABLE IF NOT EXISTS knowledge_category (
                                                  id BIGINT PRIMARY KEY AUTO_INCREMENT,
                                                  name VARCHAR(64) NOT NULL COMMENT '分类名称',
                                                  code VARCHAR(64) NOT NULL COMMENT '分类编码',
                                                  sort_order INT NOT NULL DEFAULT 0 COMMENT '排序',
                                                  enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
                                                  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                                  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                                                  UNIQUE KEY uk_knowledge_category_name (name),
                                                  UNIQUE KEY uk_knowledge_category_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------
-- 10. AI 知识库标签表
-- ----------------------------
CREATE TABLE IF NOT EXISTS knowledge_tag (
                                             id BIGINT PRIMARY KEY AUTO_INCREMENT,
                                             name VARCHAR(64) NOT NULL COMMENT '标签名称',
                                             sort_order INT NOT NULL DEFAULT 0 COMMENT '排序',
                                             enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
                                             created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                             updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                                             UNIQUE KEY uk_knowledge_tag_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------
-- 11. AI 知识库文章表
-- ----------------------------
CREATE TABLE IF NOT EXISTS knowledge_article (
                                                 id BIGINT PRIMARY KEY AUTO_INCREMENT,
                                                 title VARCHAR(200) NOT NULL COMMENT '标题',
                                                 summary VARCHAR(500) NULL COMMENT '摘要',
                                                 type VARCHAR(32) NOT NULL COMMENT '类型: PROMPT/WORKFLOW/CASE/NOTE',
                                                 category_id BIGINT NULL COMMENT '分类ID',
                                                 content_md MEDIUMTEXT NOT NULL COMMENT 'Markdown 内容',
                                                 extra_json MEDIUMTEXT NULL COMMENT '结构化扩展字段',
                                                 cover_url VARCHAR(512) NULL COMMENT '封面地址',
                                                 visibility VARCHAR(16) NOT NULL DEFAULT 'PUBLIC' COMMENT '可见性: PUBLIC/PRIVATE',
                                                 status VARCHAR(16) NOT NULL DEFAULT 'DRAFT' COMMENT '状态: DRAFT/PUBLISHED',
                                                 view_count INT NOT NULL DEFAULT 0 COMMENT '浏览数',
                                                 like_count INT NOT NULL DEFAULT 0 COMMENT '点赞数',
                                                 favorite_count INT NOT NULL DEFAULT 0 COMMENT '收藏数',
                                                 created_by VARCHAR(64) NOT NULL DEFAULT 'system' COMMENT '创建人',
                                                 updated_by VARCHAR(64) NOT NULL DEFAULT 'system' COMMENT '更新人',
                                                 published_at TIMESTAMP NULL DEFAULT NULL COMMENT '发布时间',
                                                 deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除',
                                                 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                                 updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                                                 KEY idx_knowledge_article_type_status (type, status),
                                                 KEY idx_knowledge_article_category (category_id),
                                                 KEY idx_knowledge_article_published (published_at),
                                                 CONSTRAINT fk_knowledge_article_category
                                                     FOREIGN KEY (category_id) REFERENCES knowledge_category(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------
-- 12. AI 知识库文章标签关系表
-- ----------------------------
CREATE TABLE IF NOT EXISTS knowledge_article_tag (
                                                     id BIGINT PRIMARY KEY AUTO_INCREMENT,
                                                     article_id BIGINT NOT NULL COMMENT '文章ID',
                                                     tag_id BIGINT NOT NULL COMMENT '标签ID',
                                                     UNIQUE KEY uk_knowledge_article_tag (article_id, tag_id),
                                                     KEY idx_knowledge_article_tag_tag (tag_id),
                                                     CONSTRAINT fk_knowledge_article_tag_article
                                                         FOREIGN KEY (article_id) REFERENCES knowledge_article(id)
                                                             ON DELETE CASCADE,
                                                     CONSTRAINT fk_knowledge_article_tag_tag
                                                         FOREIGN KEY (tag_id) REFERENCES knowledge_tag(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------
-- 13. AI 知识库分享链接表
-- ----------------------------
CREATE TABLE IF NOT EXISTS knowledge_share_link (
                                                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                                                    article_id BIGINT NOT NULL COMMENT '文章ID',
                                                    share_token VARCHAR(64) NOT NULL COMMENT '分享令牌',
                                                    expire_at TIMESTAMP NULL DEFAULT NULL COMMENT '过期时间',
                                                    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态: ACTIVE/DISABLED',
                                                    view_count INT NOT NULL DEFAULT 0 COMMENT '访问次数',
                                                    created_by VARCHAR(64) NOT NULL DEFAULT 'system' COMMENT '创建人',
                                                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                                    UNIQUE KEY uk_knowledge_share_link_token (share_token),
                                                    KEY idx_knowledge_share_link_article_status (article_id, status),
                                                    CONSTRAINT fk_knowledge_share_link_article
                                                        FOREIGN KEY (article_id) REFERENCES knowledge_article(id)
                                                            ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------
-- 14. 知识库基础分类与标签
-- ----------------------------
INSERT INTO knowledge_category (name, code, sort_order)
SELECT '提示词', 'prompt', 10
WHERE NOT EXISTS (SELECT 1 FROM knowledge_category WHERE code = 'prompt');

INSERT INTO knowledge_category (name, code, sort_order)
SELECT '工作流', 'workflow', 20
WHERE NOT EXISTS (SELECT 1 FROM knowledge_category WHERE code = 'workflow');

INSERT INTO knowledge_category (name, code, sort_order)
SELECT '实战案例', 'case', 30
WHERE NOT EXISTS (SELECT 1 FROM knowledge_category WHERE code = 'case');

INSERT INTO knowledge_category (name, code, sort_order)
SELECT '经验笔记', 'note', 40
WHERE NOT EXISTS (SELECT 1 FROM knowledge_category WHERE code = 'note');

INSERT INTO knowledge_tag (name, sort_order)
SELECT 'Spring AI', 10
WHERE NOT EXISTS (SELECT 1 FROM knowledge_tag WHERE name = 'Spring AI');

INSERT INTO knowledge_tag (name, sort_order)
SELECT 'RAG', 20
WHERE NOT EXISTS (SELECT 1 FROM knowledge_tag WHERE name = 'RAG');

INSERT INTO knowledge_tag (name, sort_order)
SELECT '提示工程', 30
WHERE NOT EXISTS (SELECT 1 FROM knowledge_tag WHERE name = '提示工程');

INSERT INTO knowledge_tag (name, sort_order)
SELECT '工作流', 40
WHERE NOT EXISTS (SELECT 1 FROM knowledge_tag WHERE name = '工作流');

INSERT INTO knowledge_tag (name, sort_order)
SELECT 'MCP', 50
WHERE NOT EXISTS (SELECT 1 FROM knowledge_tag WHERE name = 'MCP');
