-- =====================================================================
-- 字段库 code 列与唯一键（risk-console-redesign，R3.1/R3.4/R3.5）
--
-- 现有 field_library 仅有展示用 name（uk_field_name），缺少英文 code。
-- 本迁移为字段库新增英文 code 列，并以数据库唯一键 uk_field_code 保证
-- code 真实唯一（R3.4：以唯一键 + 精确等值查询实现去重，禁止前缀/模糊匹配）。
--
-- 幂等说明：本地及目标库为 MySQL 5.7，不支持 ADD COLUMN IF NOT EXISTS / 条件建索引，
-- 故通过 information_schema 判定列与索引是否已存在，仅在缺失时新增，
-- 保证迁移重复执行不报错、不丢数据（R14.3）。
--
-- 既有数据回填：历史行 code 为空时以 name 回填，因 name 原本唯一（uk_field_name），
-- 回填后 code 同样唯一，可安全建立唯一键。
-- =====================================================================

DROP PROCEDURE IF EXISTS rcr_add_field_code;

DELIMITER $$
CREATE PROCEDURE rcr_add_field_code()
BEGIN
    -- 1) 新增 code 列（英文字段标识）
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'field_library'
          AND COLUMN_NAME = 'code'
    ) THEN
        ALTER TABLE field_library
            ADD COLUMN code VARCHAR(64) NULL COMMENT '字段英文标识（唯一）' AFTER id;
    END IF;

    -- 2) 回填历史行：code 为空时以 name 回填，保证唯一键可建立
    UPDATE field_library SET code = name WHERE code IS NULL OR code = '';

    -- 3) 建立 code 唯一键（精确等值去重的数据库保障，R3.4）
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'field_library'
          AND INDEX_NAME = 'uk_field_code'
    ) THEN
        ALTER TABLE field_library
            ADD UNIQUE KEY uk_field_code (code);
    END IF;
END$$
DELIMITER ;

CALL rcr_add_field_code();

DROP PROCEDURE IF EXISTS rcr_add_field_code;
