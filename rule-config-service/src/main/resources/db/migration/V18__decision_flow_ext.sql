-- =====================================================================
-- 产品对齐增强 - 阶段二决策流扩展
-- 扩展既有 decision_flow（场景/多事件/适用机构）+ 新增 decision_flow_version
-- Requirements: 6.1, 6.5
-- =====================================================================

-- ---------------------------------------------------------------------
-- 扩展既有 decision_flow 表（见 V5__decision_flow.sql）
--   新增多场景 / 多事件 / 适用机构 / 含下级 列
--   节点与边的扩展（新增节点类型、trafficPercent/isDefault 等）
--   继续存于既有 nodes_json / edges_json 文本列，无需改结构
-- ---------------------------------------------------------------------
ALTER TABLE decision_flow
    ADD COLUMN scenario_ids_json LONGTEXT NULL COMMENT '关联场景ID列表(JSON数组)' AFTER event_type_code,
    ADD COLUMN event_codes_json  LONGTEXT NULL COMMENT '关联多事件类型编码列表(JSON数组)' AFTER scenario_ids_json,
    ADD COLUMN applicable_org_id BIGINT   NULL COMMENT '适用机构ID' AFTER event_codes_json,
    ADD COLUMN include_sub_org   TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否含下级机构 0否1是' AFTER applicable_org_id;

-- ---------------------------------------------------------------------
-- 决策流版本（decision_flow_version）：版本对比用，存整流快照
-- ---------------------------------------------------------------------
CREATE TABLE decision_flow_version (
    id               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    decision_flow_id BIGINT       NOT NULL COMMENT '决策流ID',
    version          INT          NOT NULL COMMENT '版本号',
    snapshot_json    LONGTEXT     NOT NULL COMMENT '决策流整体快照(JSON)',
    created_by       VARCHAR(64)  NULL COMMENT '创建人',
    created_at       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_dfv_flow (decision_flow_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='决策流版本（扩展阶段）';
