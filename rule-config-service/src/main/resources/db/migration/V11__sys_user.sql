-- =====================================================================
-- S10 权限体系：系统用户（RBAC）
-- 内置 admin 用户由应用启动时的 Seeder 写入（使用 BCrypt 哈希），此处仅建表。
-- =====================================================================
CREATE TABLE sys_user (
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    username      VARCHAR(64)  NOT NULL COMMENT '用户名',
    password_hash VARCHAR(100) NOT NULL COMMENT 'BCrypt 密码哈希',
    roles         VARCHAR(128) NOT NULL COMMENT '角色（逗号分隔）ADMIN/OPERATOR/AUDITOR',
    enabled       TINYINT      NOT NULL DEFAULT 1 COMMENT '0=禁用 1=启用',
    created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_user_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统用户（S10）';
