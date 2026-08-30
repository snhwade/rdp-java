-- AI 训练定时计划：按 cron 自动触发训练（滑动数据窗口）
CREATE TABLE ai_training_schedule (
    id                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    name              VARCHAR(128) NOT NULL COMMENT '计划名称',
    enabled           TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '是否启用',
    cron_expression   VARCHAR(64)  NOT NULL COMMENT 'Cron 五段式，如 0 2 * * *',
    window_days       INT          NOT NULL DEFAULT 30 COMMENT '训练数据滑动窗口天数',
    last_triggered_at DATETIME(3)  NULL COMMENT '上次触发时间',
    last_job_id       VARCHAR(64)  NULL COMMENT '上次产生的训练任务 job_id',
    last_run_status   VARCHAR(20)  NULL COMMENT '上次运行结果 SUCCESS/FAILED/SKIPPED',
    last_fail_reason  VARCHAR(255) NULL COMMENT '上次跳过或失败原因',
    created_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_ai_training_schedule_name (name),
    KEY idx_ai_training_schedule_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 训练定时计划';
