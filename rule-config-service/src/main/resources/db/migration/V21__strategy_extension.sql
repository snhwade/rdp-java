-- =====================================================================
-- 验证策略扩展（risk-console-redesign）
-- 为 strategy_def 新增：优先级、作用域场景ID、是否不限业务场景
-- 保留既有策略数据不变（R5.9）。
--
-- 语义：
--   priority          验证策略优先级，1..9999，数值越小优先级越高
--   scope_scenario_id 作用域场景ID（NULL + any_scope=1 表示不限业务场景）
--   any_scope         是否不限业务场景 0/1
--
-- 幂等说明：本地及目标库为 MySQL 5.7，不支持 `ADD COLUMN IF NOT EXISTS`，
-- 故通过 information_schema 判定列是否已存在，仅在缺失时新增，
-- 保证迁移重复执行不报错、不丢数据（R14.3）。
-- =====================================================================

DROP PROCEDURE IF EXISTS rcr_add_strategy_extension_columns;

DELIMITER $$
CREATE PROCEDURE rcr_add_strategy_extension_columns()
BEGIN
    -- 验证策略优先级 1..9999 越小优先级越高
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'strategy_def'
          AND COLUMN_NAME = 'priority'
    ) THEN
        ALTER TABLE strategy_def
            ADD COLUMN priority INT NULL COMMENT '验证策略优先级 1..9999 越小优先级越高' AFTER name;
    END IF;

    -- 作用域场景ID（NULL + any_scope=1 表示不限业务场景）
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'strategy_def'
          AND COLUMN_NAME = 'scope_scenario_id'
    ) THEN
        ALTER TABLE strategy_def
            ADD COLUMN scope_scenario_id BIGINT NULL COMMENT '作用域场景ID（NULL+any_scope=不限）' AFTER priority;
    END IF;

    -- 是否不限业务场景 0/1
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'strategy_def'
          AND COLUMN_NAME = 'any_scope'
    ) THEN
        ALTER TABLE strategy_def
            ADD COLUMN any_scope TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否不限业务场景' AFTER scope_scenario_id;
    END IF;
END$$
DELIMITER ;

CALL rcr_add_strategy_extension_columns();

DROP PROCEDURE IF EXISTS rcr_add_strategy_extension_columns;
