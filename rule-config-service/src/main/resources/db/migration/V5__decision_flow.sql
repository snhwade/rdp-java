-- =====================================================================
-- S4 决策流（Decision Flow）：节点 + 有向边 + 起始节点
-- nodes/edges 以 JSON 文本存储
-- =====================================================================
CREATE TABLE decision_flow (
    id               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    name             VARCHAR(128) NOT NULL COMMENT '决策流名称',
    event_type_code  VARCHAR(64)  NOT NULL COMMENT '关联事件类型',
    nodes_json       MEDIUMTEXT   NOT NULL COMMENT '节点定义(JSON)',
    edges_json       MEDIUMTEXT   NOT NULL COMMENT '边定义(JSON)',
    start_node_id    VARCHAR(64)  NOT NULL COMMENT '起始节点 id',
    status           VARCHAR(16)  NOT NULL DEFAULT 'ENABLED' COMMENT 'ENABLED/DISABLED',
    created_at       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_df_event_type (event_type_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='决策流（S4）';
