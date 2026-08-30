-- =====================================================================
-- 产品对齐增强 - 阶段一试运行（影子模式）表
-- 试运行任务 dry_run_job
-- Requirements: 5.1, 5.3
-- =====================================================================

-- ---------------------------------------------------------------------
-- 试运行任务（dry_run_job）
-- 异步影子评估目标规则/规则包，统计命中数/命中率/明细，不写决策日志
-- ---------------------------------------------------------------------
CREATE TABLE dry_run_job (
    id            BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    target_type   VARCHAR(16)   NOT NULL COMMENT '目标类型 RULE/RULE_PACKAGE',
    target_id     BIGINT        NOT NULL COMMENT '目标ID（规则或规则包ID）',
    sample_source VARCHAR(16)   NOT NULL COMMENT '样本来源 ORDER/EVENT',
    data_from     DATETIME(3)   NULL COMMENT '样本数据起始时间',
    data_to       DATETIME(3)   NULL COMMENT '样本数据结束时间',
    sample_limit  INT           NOT NULL DEFAULT 0 COMMENT '样本数量上限（0=不限）',
    status        VARCHAR(16)   NOT NULL DEFAULT 'RUNNING' COMMENT '任务状态 RUNNING/SUCCESS/FAILED',
    total_count   INT           NOT NULL DEFAULT 0 COMMENT '样本总数',
    hit_count     INT           NOT NULL DEFAULT 0 COMMENT '命中数',
    hit_rate      DECIMAL(9,6)  NULL COMMENT '命中率（hit_count/total_count）',
    error_count   INT           NOT NULL DEFAULT 0 COMMENT '单样本异常隔离计数',
    report_json   LONGTEXT      NULL COMMENT '试运行报告（总分分布/区间命中/明细摘要）',
    created_by    VARCHAR(64)   NULL COMMENT '发起人',
    started_at    DATETIME(3)   NULL COMMENT '开始时间',
    finished_at   DATETIME(3)   NULL COMMENT '完成时间',
    PRIMARY KEY (id),
    KEY idx_dry_run_target (target_type, target_id),
    KEY idx_dry_run_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='试运行任务（阶段一影子模式）';
