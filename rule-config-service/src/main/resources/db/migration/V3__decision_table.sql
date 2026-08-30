-- =====================================================================
-- S2 决策表（Decision Table）：输入变量列 + 条件行 + 命中策略
-- columns/rows 以 JSON 文本存储
-- =====================================================================
CREATE TABLE decision_table (
    id               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    name             VARCHAR(128) NOT NULL COMMENT '决策表名称',
    event_type_code  VARCHAR(64)  NOT NULL COMMENT '关联事件类型',
    hit_policy       VARCHAR(16)  NOT NULL DEFAULT 'COLLECT' COMMENT '命中策略 FIRST/COLLECT',
    columns_json     TEXT         NOT NULL COMMENT '输入变量列定义(JSON)',
    rows_json        MEDIUMTEXT   NOT NULL COMMENT '条件行定义(JSON)',
    status           VARCHAR(16)  NOT NULL DEFAULT 'ENABLED' COMMENT 'ENABLED/DISABLED',
    created_at       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_dt_event_type (event_type_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='决策表（S2）';
