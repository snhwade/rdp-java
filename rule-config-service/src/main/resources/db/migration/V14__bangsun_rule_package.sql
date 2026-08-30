-- =====================================================================
-- 产品对齐增强 - 阶段一规则包相关表
-- 规则包（rule_package）/ 规则包-场景 / 规则包-事件 / 评分分值区间 / 规则包-规则
-- Requirements: 1.1, 1.4, 1.5, 1.6
-- =====================================================================

-- ---------------------------------------------------------------------
-- 规则包（rule_package）
-- 触发模式 HIT=命中模式 / SCORE=评分模式（创建后不可变更）
-- 唯一约束：同一触发模式下编码唯一
-- ---------------------------------------------------------------------
CREATE TABLE rule_package (
    id                  BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键',
    code                VARCHAR(64)    NOT NULL COMMENT '规则包编码（同触发模式下唯一）',
    name                VARCHAR(128)   NOT NULL COMMENT '规则包名称',
    trigger_mode        VARCHAR(16)    NOT NULL COMMENT '触发模式 HIT=命中模式/SCORE=评分模式（创建后不可变）',
    compute_mode        VARCHAR(16)    NOT NULL DEFAULT 'ONLINE' COMMENT '计算方式 ONLINE=在线/OFFLINE=离线',
    risk_type_code      VARCHAR(64)    NULL COMMENT '风险类型编码',
    owner_org_id        BIGINT         NULL COMMENT '所属机构ID',
    applicable_org_id   BIGINT         NULL COMMENT '适用机构ID',
    include_sub_org     TINYINT(1)     NOT NULL DEFAULT 0 COMMENT '适用机构是否含下级 0=不含/1=含',
    status              VARCHAR(16)    NOT NULL DEFAULT 'DISABLED' COMMENT '状态 ENABLED/DISABLED',
    warn_score_enabled  TINYINT(1)     NOT NULL DEFAULT 0 COMMENT '是否启用生成预警单分值阈值（评分模式）',
    warn_score_op       VARCHAR(8)     NULL COMMENT '预警单阈值比较符 GTE=大于等于/LT=小于',
    warn_score_threshold DECIMAL(18,4) NULL COMMENT '预警单分值阈值（可空）',
    version             INT            NOT NULL DEFAULT 1 COMMENT '版本号',
    create_user         VARCHAR(64)    NULL COMMENT '创建人',
    create_time         DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_user         VARCHAR(64)    NULL COMMENT '更新人',
    update_time         DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_rule_package_mode_code (trigger_mode, code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='规则包（阶段一）';

-- ---------------------------------------------------------------------
-- 规则包-场景关联（多对多）
-- ---------------------------------------------------------------------
CREATE TABLE rule_package_scenario (
    id               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    rule_package_id  BIGINT       NOT NULL COMMENT '规则包ID',
    scenario_id      BIGINT       NOT NULL COMMENT '场景ID',
    PRIMARY KEY (id),
    UNIQUE KEY uk_rule_package_scenario (rule_package_id, scenario_id),
    KEY idx_rule_package_scenario_pkg (rule_package_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='规则包-场景关联（阶段一）';

-- ---------------------------------------------------------------------
-- 规则包-决策事件关联（多对多）
-- ---------------------------------------------------------------------
CREATE TABLE rule_package_event (
    id               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    rule_package_id  BIGINT       NOT NULL COMMENT '规则包ID',
    event_type_code  VARCHAR(64)  NOT NULL COMMENT '决策事件类型编码',
    PRIMARY KEY (id),
    UNIQUE KEY uk_rule_package_event (rule_package_id, event_type_code),
    KEY idx_rule_package_event_pkg (rule_package_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='规则包-决策事件关联（阶段一）';

-- ---------------------------------------------------------------------
-- 评分模式分值区间（rule_package_score_band）
-- 区间之间不重叠、可含负分；输出策略经 score_band_strategy 关联
-- ---------------------------------------------------------------------
CREATE TABLE rule_package_score_band (
    id               BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键',
    rule_package_id  BIGINT         NOT NULL COMMENT '规则包ID',
    lower            DECIMAL(18,4)  NULL COMMENT '区间下界（可为负，空表示负无穷）',
    upper            DECIMAL(18,4)  NULL COMMENT '区间上界（空表示正无穷）',
    lower_inclusive  TINYINT(1)     NOT NULL DEFAULT 1 COMMENT '下界是否包含',
    upper_inclusive  TINYINT(1)     NOT NULL DEFAULT 0 COMMENT '上界是否包含',
    risk_level_code  VARCHAR(64)    NULL COMMENT '映射风险等级编码',
    order_no         INT            NOT NULL DEFAULT 0 COMMENT '排序号',
    PRIMARY KEY (id),
    KEY idx_rule_package_score_band_pkg (rule_package_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评分模式分值区间（阶段一）';

-- ---------------------------------------------------------------------
-- 规则包-规则关联（多对多，支持规则在多个规则包中，含包内优先级）
-- ---------------------------------------------------------------------
CREATE TABLE rule_package_rule (
    id               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    rule_package_id  BIGINT       NOT NULL COMMENT '规则包ID',
    rule_v2_id       BIGINT       NOT NULL COMMENT '结构化规则ID',
    priority         INT          NOT NULL DEFAULT 0 COMMENT '包内规则优先级（数值越大优先级越高）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_rule_package_rule (rule_package_id, rule_v2_id),
    KEY idx_rule_package_rule_pkg (rule_package_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='规则包-规则关联（阶段一）';
