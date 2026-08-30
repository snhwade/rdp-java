-- =====================================================================
-- S9 决策矩阵（Decision Matrix）：行维度区间 × 列维度区间 → 单元格决策
-- bins/cells 以 JSON 文本存储
-- =====================================================================
CREATE TABLE decision_matrix (
    id               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    name             VARCHAR(128) NOT NULL COMMENT '决策矩阵名称',
    event_type_code  VARCHAR(64)  NOT NULL COMMENT '关联事件类型',
    row_var          VARCHAR(64)  NOT NULL COMMENT '行维度变量',
    row_bins_json    TEXT         NOT NULL COMMENT '行维度区间(JSON)',
    col_var          VARCHAR(64)  NOT NULL COMMENT '列维度变量',
    col_bins_json    TEXT         NOT NULL COMMENT '列维度区间(JSON)',
    cells_json       MEDIUMTEXT   NOT NULL COMMENT '单元格(JSON)',
    status           VARCHAR(16)  NOT NULL DEFAULT 'ENABLED' COMMENT 'ENABLED/DISABLED',
    created_at       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_dmatrix_event_type (event_type_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='决策矩阵（S9）';
