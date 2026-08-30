-- =====================================================================
-- 指标配置优化（IR1/IS1/IV1）：定义快照 + 运行统计
-- =====================================================================

CREATE TABLE IF NOT EXISTS indicator_definition_snapshot (
    id                      BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    indicator_definition_id BIGINT       NOT NULL COMMENT '指标定义ID',
    version                 INT          NOT NULL COMMENT '定义内递增版本号（从1起）',
    snapshot_json           LONGTEXT     NOT NULL COMMENT '更新前定义快照(JSON)',
    created_by              VARCHAR(64)  NULL COMMENT '创建人',
    created_at              DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_ids_def_ver (indicator_definition_id, version),
    KEY idx_ids_def (indicator_definition_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='指标定义快照（IV1）';

CREATE TABLE IF NOT EXISTS indicator_runtime_stats (
    ref_name             VARCHAR(64)  NOT NULL COMMENT '指标引用名',
    last_accumulate_at   DATETIME(3)  NULL COMMENT '最近累计时间',
    read_miss_count      BIGINT       NOT NULL DEFAULT 0 COMMENT '读缺失次数',
    updated_at           DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (ref_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='指标运行统计（IS1）';
