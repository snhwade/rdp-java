-- 逻辑指标（方案 C）：产品层一个虚拟 refName，运行时由多个物理指标成员聚合
CREATE TABLE logical_indicator (
    id                    BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    ref_name              VARCHAR(64)  NOT NULL COMMENT '虚拟指标引用名（规则读取用）',
    name                  VARCHAR(128) NULL COMMENT '展示名称',
    description           TEXT         NULL COMMENT '描述',
    group_id              BIGINT       NULL COMMENT '所属指标分组',
    combine_mode          VARCHAR(16)  NOT NULL DEFAULT 'SUM' COMMENT 'SUM|EXPRESSION',
    combine_expression    VARCHAR(512) NULL COMMENT 'EXPRESSION 模式 Aviator 表达式，变量为成员 refName',
    dimensions            JSON         NOT NULL COMMENT '统计维度（须与成员一致）',
    window_days           INT          NOT NULL DEFAULT 1 COMMENT '读取窗口天数',
    slice_granularity     VARCHAR(16)  NOT NULL DEFAULT 'DAY' COMMENT 'MINUTE|HOUR|DAY',
    default_value_strategy VARCHAR(32) NULL COMMENT '缺失默认值策略',
    status                VARCHAR(16)  NOT NULL DEFAULT 'OFFLINE' COMMENT 'ONLINE|OFFLINE',
    created_at            DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at            DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_logical_indicator_ref (ref_name),
    KEY idx_logical_indicator_group (group_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='逻辑指标（虚拟 refName）';

CREATE TABLE logical_indicator_member (
    id               BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    logical_id       BIGINT      NOT NULL COMMENT '逻辑指标 id',
    member_ref_name  VARCHAR(64) NOT NULL COMMENT '物理指标 refName',
    event_type_code  VARCHAR(64) NULL COMMENT '绑定事件（展示/metadata）',
    sort_order       INT         NOT NULL DEFAULT 0 COMMENT '成员顺序',
    PRIMARY KEY (id),
    UNIQUE KEY uk_logical_member (logical_id, member_ref_name),
    KEY idx_logical_member_ref (member_ref_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='逻辑指标成员（物理 refName）';
