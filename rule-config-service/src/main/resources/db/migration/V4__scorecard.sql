-- =====================================================================
-- S3 评分卡（Scorecard）：变量(条件区间→分值×权重) + 等级区间(总分→等级+决策)
-- variables/levels 以 JSON 文本存储
-- =====================================================================
CREATE TABLE scorecard (
    id               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    name             VARCHAR(128) NOT NULL COMMENT '评分卡名称',
    event_type_code  VARCHAR(64)  NOT NULL COMMENT '关联事件类型',
    variables_json   MEDIUMTEXT   NOT NULL COMMENT '评分变量定义(JSON)',
    levels_json      TEXT         NOT NULL COMMENT '等级区间定义(JSON)',
    status           VARCHAR(16)  NOT NULL DEFAULT 'ENABLED' COMMENT 'ENABLED/DISABLED',
    created_at       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_sc_event_type (event_type_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评分卡（S3）';
