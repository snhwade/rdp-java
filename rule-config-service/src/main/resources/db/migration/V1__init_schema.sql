-- =====================================================================
-- 风控实时决策平台 初始 schema（V1）
-- 设计依据：design.md / Data Models（MySQL 表设计）
-- 引擎 InnoDB，字符集 utf8mb4。所有表含审计字段 created_at/updated_at/created_by/updated_by。
-- 敏感字段（交易主体名称、证件号、上下文）由应用层加密后落库（R17.4）。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 事件类型（R1）
-- ---------------------------------------------------------------------
CREATE TABLE event_type (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    code        VARCHAR(64)  NOT NULL COMMENT '唯一 code，[A-Za-z0-9_]',
    name        VARCHAR(100) NOT NULL COMMENT '名称 1..100',
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '0=禁用 1=启用',
    created_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    created_by  VARCHAR(64)  NULL,
    updated_by  VARCHAR(64)  NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_event_type_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='事件类型';

-- ---------------------------------------------------------------------
-- 规则（R3）
-- ---------------------------------------------------------------------
CREATE TABLE rule (
    id                 BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    event_type_code    VARCHAR(64) NOT NULL COMMENT '关联事件类型',
    expression         TEXT        NOT NULL COMMENT 'Aviator 表达式 1..4000',
    referenced_fields  JSON        NULL COMMENT '引用字段集合（声明校验 R3.5/3.6）',
    decision           VARCHAR(20) NOT NULL COMMENT 'PASS/REVIEW/REJECT',
    short_circuited    TINYINT     NOT NULL DEFAULT 0 COMMENT '是否短路 R5.6',
    version            INT         NOT NULL DEFAULT 1 COMMENT '版本号，更新+1',
    status             TINYINT     NOT NULL DEFAULT 1 COMMENT '0=禁用 1=启用',
    created_at         DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at         DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    created_by         VARCHAR(64) NULL,
    updated_by         VARCHAR(64) NULL,
    PRIMARY KEY (id),
    KEY idx_rule_event_type (event_type_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='规则';

-- ---------------------------------------------------------------------
-- 规则组（R4）
-- ---------------------------------------------------------------------
CREATE TABLE rule_group (
    id               BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    event_type_code  VARCHAR(64) NOT NULL COMMENT '关联事件类型',
    name             VARCHAR(100) NULL COMMENT '规则组名称',
    status           TINYINT     NOT NULL DEFAULT 1 COMMENT '0=禁用 1=启用 R4.8',
    created_at       DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at       DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    created_by       VARCHAR(64) NULL,
    updated_by       VARCHAR(64) NULL,
    PRIMARY KEY (id),
    KEY idx_rule_group_event_type (event_type_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='规则组';

-- ---------------------------------------------------------------------
-- 规则组-规则关联（组内规则优先级）
-- ---------------------------------------------------------------------
CREATE TABLE rule_group_rule (
    id          BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    group_id    BIGINT NOT NULL COMMENT '规则组 id',
    rule_id     BIGINT NOT NULL COMMENT '规则 id',
    priority    INT    NOT NULL DEFAULT 100 COMMENT '组内规则优先级（数值越小越高 R5.1）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_group_rule (group_id, rule_id),
    KEY idx_group_rule_group (group_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='规则组-规则关联';

-- ---------------------------------------------------------------------
-- 规则选择器（R4）
-- ---------------------------------------------------------------------
CREATE TABLE rule_selector (
    id                  BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键（同优先级取最小 R4.5）',
    event_type_code     VARCHAR(64) NOT NULL COMMENT '关联事件类型',
    priority            INT         NOT NULL DEFAULT 100 COMMENT '选择器优先级（数值越小越高 R4.4）',
    match_type          VARCHAR(20) NOT NULL COMMENT 'SIMPLE_KV/SELECTOR_RULE/FALLBACK',
    select_key          VARCHAR(128) NULL COMMENT 'SIMPLE_KV 用',
    select_value        VARCHAR(256) NULL COMMENT 'SIMPLE_KV 用',
    selector_expression TEXT        NULL COMMENT 'SELECTOR_RULE 用',
    rule_group_id       BIGINT      NOT NULL COMMENT '关联规则组',
    status              TINYINT     NOT NULL DEFAULT 1 COMMENT '0=禁用 1=启用',
    created_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    created_by          VARCHAR(64) NULL,
    updated_by          VARCHAR(64) NULL,
    PRIMARY KEY (id),
    KEY idx_selector_event_type (event_type_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='规则选择器';

-- ---------------------------------------------------------------------
-- 决策优先级配置（R6）
-- ---------------------------------------------------------------------
CREATE TABLE decision_priority_config (
    id                   BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    rule_id              BIGINT      NOT NULL COMMENT '规则 id',
    priority             INT         NOT NULL COMMENT '1..9999 决策优先级 R6.1/6.10',
    timeout_ms           INT         NOT NULL DEFAULT 500 COMMENT '决策时限 1..5000，默认 500 R6.5',
    timeout_disposition  VARCHAR(20) NOT NULL DEFAULT 'REVIEW' COMMENT '超时处置策略 R6.7',
    created_at           DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at           DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    created_by           VARCHAR(64) NULL,
    updated_by           VARCHAR(64) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_decision_priority_rule (rule_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='决策优先级配置';

-- ---------------------------------------------------------------------
-- 指标定义（R7）
-- ---------------------------------------------------------------------
CREATE TABLE indicator_definition (
    id                     BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    ref_name               VARCHAR(64) NOT NULL COMMENT '引用名 [A-Za-z0-9_] R7.1/7.3',
    dimensions             JSON        NOT NULL COMMENT '统计维度',
    window_days            INT         NOT NULL COMMENT '1..365 R7.5',
    slice_granularity      VARCHAR(10) NOT NULL COMMENT 'MINUTE/HOUR/DAY R7.5',
    acc_script             TEXT        NOT NULL COMMENT '累计脚本 Aviator R7.1/7.4',
    default_value_strategy VARCHAR(50) NULL COMMENT '缺失默认取值 R16.3',
    created_at             DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at             DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    created_by             VARCHAR(64) NULL,
    updated_by             VARCHAR(64) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_indicator_ref_name (ref_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='指标定义';

-- ---------------------------------------------------------------------
-- 事中订单（R10）
-- ---------------------------------------------------------------------
CREATE TABLE risk_order (
    id               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    event_id         VARCHAR(64)  NOT NULL COMMENT '事件标识，至多一条记录 R10.1',
    merchant_id      VARCHAR(64)  NULL COMMENT '商户（查询过滤 R10.4）',
    event_type_code  VARCHAR(64)  NULL COMMENT '事件类型（查询过滤）',
    context          LONGTEXT     NULL COMMENT '事件上下文（敏感加密 R17.4）',
    final_decision   VARCHAR(20)  NULL COMMENT '最终决策 R10.2',
    event_time       DATETIME(3)  NULL COMMENT '受理时间（毫秒，时间范围过滤 R10.4）',
    created_at       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    created_by       VARCHAR(64)  NULL,
    updated_by       VARCHAR(64)  NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_risk_order_event (event_id),
    KEY idx_risk_order_merchant (merchant_id),
    KEY idx_risk_order_event_type (event_type_code),
    KEY idx_risk_order_event_time (event_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='事中订单';

-- ---------------------------------------------------------------------
-- 决策日志（R6/R15）
-- ---------------------------------------------------------------------
CREATE TABLE decision_log (
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    event_id        VARCHAR(64)  NOT NULL COMMENT '事件标识',
    final_decision  VARCHAR(20)  NOT NULL COMMENT '最终决策 R15.1',
    hit_rules       JSON         NULL COMMENT '命中规则及各自决策/优先级 R6.6',
    elapsed_ms      INT          NULL COMMENT '处理耗时 R15.1',
    timeout_reason  VARCHAR(255) NULL COMMENT '超时原因（若有）R6.7',
    group_status    VARCHAR(20)  NULL COMMENT '规则组执行状态（含 INTERRUPTED R5.4）',
    created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_decision_log_event (event_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='决策日志';

-- ---------------------------------------------------------------------
-- 商户评级（R12）
-- ---------------------------------------------------------------------
CREATE TABLE merchant_rating (
    merchant_id  VARCHAR(64) NOT NULL COMMENT '商户号',
    score        INT         NULL COMMENT '0..100 R12.1',
    level        VARCHAR(10) NULL COMMENT 'LOW/MID_LOW/MID/MID_HIGH/HIGH R12.2',
    status       VARCHAR(20) NOT NULL DEFAULT 'UNRATED' COMMENT 'RATED/UNRATED R12.5',
    factors      JSON        NULL COMMENT '评级因子快照',
    created_at   DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at   DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    created_by   VARCHAR(64) NULL,
    updated_by   VARCHAR(64) NULL,
    PRIMARY KEY (merchant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商户评级';

-- ---------------------------------------------------------------------
-- 名单（R11）
-- ---------------------------------------------------------------------
CREATE TABLE screening_list (
    id                    BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    source                VARCHAR(50)  NOT NULL COMMENT '名单来源（名单/制裁/道琼斯）R11.2',
    entry_name            VARCHAR(512) NOT NULL COMMENT '名单条目（加密）',
    similarity_threshold  DECIMAL(3,2) NOT NULL DEFAULT 0.85 COMMENT '阈值 0.00..1.00 默认 0.85 R11.4',
    created_at            DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at            DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    created_by            VARCHAR(64)  NULL,
    updated_by            VARCHAR(64)  NULL,
    PRIMARY KEY (id),
    KEY idx_screening_source (source)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='筛查名单';

-- ---------------------------------------------------------------------
-- 筛查结果（R11）
-- ---------------------------------------------------------------------
CREATE TABLE screening_result (
    id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    event_id     VARCHAR(64)  NOT NULL COMMENT '关联事件标识',
    subject_name VARCHAR(512) NULL COMMENT '被筛查主体名称（加密）',
    hit          TINYINT      NOT NULL DEFAULT 0 COMMENT '是否命中',
    source       VARCHAR(50)  NULL COMMENT '命中名单来源',
    matched_entry VARCHAR(512) NULL COMMENT '匹配条目',
    similarity   DECIMAL(5,4) NULL COMMENT '匹配相似度',
    created_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_screening_result_event (event_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='筛查结果';

-- ---------------------------------------------------------------------
-- 审计日志（R17.3）
-- ---------------------------------------------------------------------
CREATE TABLE audit_log (
    id           BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    operator     VARCHAR(64) NOT NULL COMMENT '操作人',
    op_time      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '操作时间',
    op_type      VARCHAR(20) NOT NULL COMMENT 'CREATE/UPDATE/DELETE',
    target_type  VARCHAR(40) NOT NULL COMMENT 'event_type/rule/rule_group/indicator',
    op_content   JSON        NULL COMMENT '操作内容',
    PRIMARY KEY (id),
    KEY idx_audit_operator (operator),
    KEY idx_audit_target (target_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审计日志';

-- ---------------------------------------------------------------------
-- AI 训练任务（R13）
-- ---------------------------------------------------------------------
CREATE TABLE ai_training_job (
    id             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    data_from      DATETIME     NULL COMMENT '数据时间范围起 R13.3',
    data_to        DATETIME     NULL COMMENT '数据时间范围止 R13.3',
    status         VARCHAR(20)  NOT NULL DEFAULT 'RUNNING' COMMENT 'RUNNING/SUCCESS/FAILED',
    model_version  VARCHAR(40)  NULL COMMENT '模型版本 R13.3',
    metrics        JSON         NULL COMMENT '评估指标 R13.3',
    fail_reason    VARCHAR(255) NULL COMMENT '失败原因 R13.7/13.11',
    created_at     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_ai_job_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 训练任务';
