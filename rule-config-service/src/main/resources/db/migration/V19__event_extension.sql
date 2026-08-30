-- =====================================================================
-- 事件参数扩展（risk-console-redesign）
-- 为 event_type 新增：所属业务场景、事件类型分型、事件用途多选
-- 保留既有 code/name/status 数据不变（R2.12）。
--
-- 幂等说明：本地及目标库为 MySQL 5.7，不支持 `ADD COLUMN IF NOT EXISTS`，
-- 故通过 information_schema 判定列是否已存在，仅在缺失时新增，
-- 保证迁移重复执行不报错、不丢数据（R14.3）。
-- =====================================================================

DROP PROCEDURE IF EXISTS rcr_add_event_extension_columns;

DELIMITER $$
CREATE PROCEDURE rcr_add_event_extension_columns()
BEGIN
    -- 所属业务场景ID
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'event_type'
          AND COLUMN_NAME = 'scenario_id'
    ) THEN
        ALTER TABLE event_type
            ADD COLUMN scenario_id BIGINT NULL COMMENT '所属业务场景ID' AFTER name;
    END IF;

    -- 事件类型分型 DIMENSION/FACT
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'event_type'
          AND COLUMN_NAME = 'event_kind'
    ) THEN
        ALTER TABLE event_type
            ADD COLUMN event_kind VARCHAR(16) NULL COMMENT '事件类型分型 DIMENSION/FACT' AFTER scenario_id;
    END IF;

    -- 事件用途多选 JSON 数组 [COMPUTE,DECISION]
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'event_type'
          AND COLUMN_NAME = 'purposes_json'
    ) THEN
        ALTER TABLE event_type
            ADD COLUMN purposes_json VARCHAR(64) NULL COMMENT '事件用途多选 JSON 数组 [COMPUTE,DECISION]' AFTER event_kind;
    END IF;
END$$
DELIMITER ;

CALL rcr_add_event_extension_columns();

DROP PROCEDURE IF EXISTS rcr_add_event_extension_columns;
