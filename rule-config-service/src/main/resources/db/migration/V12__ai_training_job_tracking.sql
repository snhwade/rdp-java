-- ---------------------------------------------------------------------
-- AI 训练任务表补全跟踪字段（S12 集成：AI 服务按 job_id upsert + 记录样本量/起止时间）
-- 原 V1 仅有自增 id；AI 训练服务以业务 job_id 作为幂等键，并记录 started_at/finished_at/
-- sample_count 以支撑任务列表与前端展示（R13.10/13.11）。
-- ---------------------------------------------------------------------
ALTER TABLE ai_training_job
    ADD COLUMN job_id        VARCHAR(64)  NULL COMMENT '业务任务标识（幂等键）' AFTER id,
    ADD COLUMN started_at    DATETIME(3)  NULL COMMENT '训练开始时间' AFTER fail_reason,
    ADD COLUMN finished_at   DATETIME(3)  NULL COMMENT '训练结束时间' AFTER started_at,
    ADD COLUMN sample_count  INT          NOT NULL DEFAULT 0 COMMENT '样本量';

-- job_id 唯一约束：支撑 INSERT ... ON DUPLICATE KEY UPDATE 的按业务键 upsert
ALTER TABLE ai_training_job
    ADD UNIQUE KEY uk_ai_job_job_id (job_id);
