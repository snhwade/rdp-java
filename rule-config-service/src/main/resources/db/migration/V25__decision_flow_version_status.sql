-- =====================================================================
-- 决策流版本上下线状态（risk-console-redesign，R8.5/R8.6/R8.7）
--
-- 现有 decision_flow_version 仅记录版本快照（id/decision_flow_id/version/
-- snapshot_json/created_by/created_at），缺少版本级上下线状态。本迁移为其
-- 新增 status 列（ONLINE/OFFLINE，默认 OFFLINE），以支撑：
--   - 版本历史展示版本号与状态（R8.5）
--   - 版本上线：目标版本置 ONLINE、原上线版本置 OFFLINE（至多一个上线，R8.6）
--   - 决策流下线：上线版本置 OFFLINE（R8.7）
--
-- 幂等说明：本地及目标库为 MySQL 5.7，不支持 ADD COLUMN IF NOT EXISTS，
-- 故通过 information_schema 判定列是否已存在，仅在缺失时新增，保证迁移
-- 重复执行不报错、不丢数据（R14.3）。
-- =====================================================================

DROP PROCEDURE IF EXISTS rcr_add_dfv_status;

DELIMITER $$
CREATE PROCEDURE rcr_add_dfv_status()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'decision_flow_version'
          AND COLUMN_NAME = 'status'
    ) THEN
        ALTER TABLE decision_flow_version
            ADD COLUMN status VARCHAR(16) NOT NULL DEFAULT 'OFFLINE'
                COMMENT '版本上下线状态 ONLINE/OFFLINE' AFTER snapshot_json;
    END IF;
END$$
DELIMITER ;

CALL rcr_add_dfv_status();

DROP PROCEDURE IF EXISTS rcr_add_dfv_status;
