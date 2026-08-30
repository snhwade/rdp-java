-- 智能决策 IA1：策略采纳模式 + 变更审计
ALTER TABLE agent_strategy
    ADD COLUMN adoption_mode VARCHAR(16) NOT NULL DEFAULT 'SHADOW'
        COMMENT '采纳模式 SHADOW/ADVISORY/STRICT/OVERRIDE' AFTER status;

CREATE TABLE agent_strategy_adoption_audit (
    id               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    strategy_id      BIGINT       NOT NULL COMMENT '策略ID',
    strategy_code    VARCHAR(64)  NOT NULL COMMENT '策略编码快照',
    from_mode        VARCHAR(16)  NULL COMMENT '变更前',
    to_mode          VARCHAR(16)  NOT NULL COMMENT '变更后',
    changed_by       VARCHAR(64)  NULL COMMENT '操作人',
    created_at       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_agent_adoption_strategy (strategy_id),
    KEY idx_agent_adoption_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent 策略采纳模式变更审计（IA1）';
