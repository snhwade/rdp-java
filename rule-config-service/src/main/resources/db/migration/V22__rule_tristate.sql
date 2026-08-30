-- =====================================================================
-- 规则三态语义（risk-console-redesign，R7.8/R14.3）
--
-- 现有 rule_v2.status 仅承载两态 ENABLED/DISABLED（VARCHAR(16)，默认 DISABLED）。
-- 本期将规则状态扩展为三态：
--   ONLINE     上线（参与最终决策聚合）
--   TRIAL_RUN  试运行（被执行并返回结果，但不参与最终决策聚合）
--   OFFLINE    下线（不被执行，默认值）
--
-- 数据映射（R7.8）：
--   ENABLED  -> ONLINE
--   DISABLED -> OFFLINE
--
-- 幂等说明：本地及目标库为 MySQL 5.7，不支持 `MODIFY COLUMN IF EXISTS`，
-- 且对列做无谓的 MODIFY 会重写整列。故通过 information_schema 判定列定义
-- （默认值/注释）是否已为三态形态，仅在尚未调整时执行 MODIFY；
-- 数据映射使用条件 UPDATE（仅命中 ENABLED/DISABLED 行），重复执行结果稳定：
-- 再次运行时 ENABLED/DISABLED 行已不存在，ONLINE/TRIAL_RUN/OFFLINE 保持不变（R14.3）。
-- =====================================================================

DROP PROCEDURE IF EXISTS rcr_extend_rule_status_tristate;

DELIMITER $$
CREATE PROCEDURE rcr_extend_rule_status_tristate()
BEGIN
    -- 1) 将 status 调整为三态形态：VARCHAR(16) NOT NULL DEFAULT 'OFFLINE'，更新注释。
    --    仅当当前默认值尚非 'OFFLINE'（即仍为两态形态）时才执行 MODIFY，避免重复重写整列。
    IF EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'rule_v2'
          AND COLUMN_NAME = 'status'
          AND (COLUMN_DEFAULT IS NULL OR COLUMN_DEFAULT <> 'OFFLINE')
    ) THEN
        ALTER TABLE rule_v2
            MODIFY COLUMN status VARCHAR(16) NOT NULL DEFAULT 'OFFLINE'
            COMMENT '规则三态 ONLINE=上线/TRIAL_RUN=试运行/OFFLINE=下线';
    END IF;

    -- 2) 既有数据映射（条件 UPDATE，幂等）：ENABLED->ONLINE、DISABLED->OFFLINE。
    UPDATE rule_v2 SET status = 'ONLINE'  WHERE status = 'ENABLED';
    UPDATE rule_v2 SET status = 'OFFLINE' WHERE status = 'DISABLED';
END$$
DELIMITER ;

CALL rcr_extend_rule_status_tristate();

DROP PROCEDURE IF EXISTS rcr_extend_rule_status_tristate;
