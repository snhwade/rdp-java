-- =====================================================================
-- 产品对齐增强 - 阶段一规则策略体系表
-- 策略定义 / 规则-策略绑定 / 评分区间-策略绑定 / 决策产出策略记录
-- Requirements: 3.1, 3.2, 3.3, 3.4
-- =====================================================================

-- ---------------------------------------------------------------------
-- 策略定义（strategy_def）四类策略共表，按 category 区分
--   VERIFY=验证 / CONTROL_STATE=状态管控 / CONTROL_LIMIT=限额管控
--   NOTIFY=通知 / LISTING=名单
-- ---------------------------------------------------------------------
CREATE TABLE strategy_def (
    id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    category     VARCHAR(16)  NOT NULL COMMENT '策略类别 VERIFY/CONTROL_STATE/CONTROL_LIMIT/NOTIFY/LISTING',
    code         VARCHAR(64)  NOT NULL COMMENT '策略编码',
    name         VARCHAR(128) NOT NULL COMMENT '策略名称',
    params_json  LONGTEXT     NULL COMMENT '策略参数JSON（如验证方式/限额类型阈值/通知渠道/目标名单）',
    status       VARCHAR(16)  NOT NULL DEFAULT 'ENABLED' COMMENT '状态 ENABLED/DISABLED',
    create_user  VARCHAR(64)  NULL COMMENT '创建人',
    create_time  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_user  VARCHAR(64)  NULL COMMENT '更新人',
    update_time  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_strategy_def_category (category)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='策略定义（阶段一，四类共表）';

-- ---------------------------------------------------------------------
-- 规则-策略绑定（rule_strategy）
-- ---------------------------------------------------------------------
CREATE TABLE rule_strategy (
    id               BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    rule_v2_id       BIGINT      NOT NULL COMMENT '结构化规则ID（rule_v2.id）',
    strategy_def_id  BIGINT      NOT NULL COMMENT '策略定义ID（strategy_def.id）',
    priority         INT         NULL COMMENT '优先级（验证策略用，数值越大优先级越高）',
    extra_json       LONGTEXT    NULL COMMENT '绑定附加参数JSON',
    PRIMARY KEY (id),
    KEY idx_rule_strategy_rule (rule_v2_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='规则-策略绑定（阶段一）';

-- ---------------------------------------------------------------------
-- 评分区间-策略绑定（score_band_strategy）
-- ---------------------------------------------------------------------
CREATE TABLE score_band_strategy (
    id               BIGINT  NOT NULL AUTO_INCREMENT COMMENT '主键',
    score_band_id    BIGINT  NOT NULL COMMENT '评分分值区间ID（rule_package_score_band.id）',
    strategy_def_id  BIGINT  NOT NULL COMMENT '策略定义ID（strategy_def.id）',
    PRIMARY KEY (id),
    KEY idx_score_band_strategy_band (score_band_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评分区间-策略绑定（阶段一）';

-- ---------------------------------------------------------------------
-- 决策产出策略记录（decision_strategy_output）只记录不下发
--   rule_v2_id 为 NULL 表示由评分区间映射产出
-- ---------------------------------------------------------------------
CREATE TABLE decision_strategy_output (
    id             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    event_id       VARCHAR(64)  NOT NULL COMMENT '事件ID',
    decision_id    BIGINT       NULL COMMENT '关联决策日志ID（decision_log.id）',
    rule_v2_id     BIGINT       NULL COMMENT '命中规则ID（NULL=区间映射产出）',
    category       VARCHAR(16)  NOT NULL COMMENT '策略类别 VERIFY/CONTROL_STATE/CONTROL_LIMIT/NOTIFY/LISTING',
    strategy_code  VARCHAR(64)  NOT NULL COMMENT '策略编码',
    payload_json   LONGTEXT     NULL COMMENT '本次输出的具体参数JSON',
    created_at     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '产出时间',
    PRIMARY KEY (id),
    KEY idx_decision_strategy_output_event (event_id),
    KEY idx_decision_strategy_output_decision (decision_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='决策产出策略记录（阶段一，只记录不下发）';
