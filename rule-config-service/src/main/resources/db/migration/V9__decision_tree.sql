-- =====================================================================
-- S8 决策树（Decision Tree）：根节点 + 节点(分支条件→子节点 / 叶子决策)
-- nodes 以 JSON 文本存储
-- =====================================================================
CREATE TABLE decision_tree (
    id               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    name             VARCHAR(128) NOT NULL COMMENT '决策树名称',
    event_type_code  VARCHAR(64)  NOT NULL COMMENT '关联事件类型',
    root_node_id     VARCHAR(64)  NOT NULL COMMENT '根节点 id',
    nodes_json       MEDIUMTEXT   NOT NULL COMMENT '节点定义(JSON)',
    status           VARCHAR(16)  NOT NULL DEFAULT 'ENABLED' COMMENT 'ENABLED/DISABLED',
    created_at       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_dtree_event_type (event_type_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='决策树（S8）';
