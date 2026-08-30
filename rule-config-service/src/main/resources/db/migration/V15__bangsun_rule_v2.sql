-- =====================================================================
-- 产品对齐增强 - 阶段一结构化规则表
-- 结构化规则（rule_v2，含条件树/编译表达式）/ 评分规则动态分（rule_dynamic_score）
-- Requirements: 2.1, 4.1, 4.2
-- =====================================================================

-- ---------------------------------------------------------------------
-- 结构化规则（rule_v2）
-- 条件以结构化条件树 JSON 存储，编译为 Aviator 表达式缓存执行
-- ---------------------------------------------------------------------
CREATE TABLE rule_v2 (
    id                BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键',
    code              VARCHAR(64)    NOT NULL COMMENT '规则编码',
    name              VARCHAR(128)   NOT NULL COMMENT '规则名称',
    rule_package_id   BIGINT         NULL COMMENT '所属规则包ID',
    rule_kind         VARCHAR(16)    NOT NULL COMMENT '规则类型 HIT=命中规则/SCORE=评分规则',
    event_type_code   VARCHAR(64)    NULL COMMENT '决策事件类型编码',
    risk_level_code   VARCHAR(64)    NULL COMMENT '风险等级编码',
    risk_type_code    VARCHAR(64)    NULL COMMENT '风险类型编码',
    base_score        DECIMAL(18,4)  NULL COMMENT '基础分值（评分规则用，可为负，命中规则为空）',
    condition_json    LONGTEXT       NULL COMMENT '结构化条件树 JSON',
    compiled_expr     TEXT           NULL COMMENT '编译后的 Aviator 表达式',
    expr_version      INT            NOT NULL DEFAULT 0 COMMENT '编译缓存版本键',
    priority          INT            NOT NULL DEFAULT 0 COMMENT '规则优先级（数值越大优先级越高）',
    short_circuited   TINYINT(1)     NOT NULL DEFAULT 0 COMMENT '是否短路（命中后停止后续规则）',
    applicable_org_id BIGINT         NULL COMMENT '适用机构ID',
    include_sub_org   TINYINT(1)     NOT NULL DEFAULT 0 COMMENT '是否含下级机构',
    remark            VARCHAR(512)   NULL COMMENT '备注',
    version           INT            NOT NULL DEFAULT 1 COMMENT '规则版本号',
    status            VARCHAR(16)    NOT NULL DEFAULT 'DISABLED' COMMENT '状态 ENABLED/DISABLED',
    create_user       VARCHAR(64)    NULL COMMENT '创建人',
    create_time       DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_user       VARCHAR(64)    NULL COMMENT '更新人',
    update_time       DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_rule_v2_package (rule_package_id),
    KEY idx_rule_v2_event (event_type_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='结构化规则（阶段一）';

-- ---------------------------------------------------------------------
-- 评分规则动态分（rule_dynamic_score）
-- 按指标取值区间（左闭右开等）配置动态得分
-- ---------------------------------------------------------------------
CREATE TABLE rule_dynamic_score (
    id                 BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键',
    rule_v2_id         BIGINT         NOT NULL COMMENT '所属结构化规则ID',
    indicator_ref_name VARCHAR(128)   NOT NULL COMMENT '评分标准指标引用名',
    lower              DECIMAL(18,4)  NULL COMMENT '区间下界',
    upper              DECIMAL(18,4)  NULL COMMENT '区间上界',
    lower_inclusive    TINYINT(1)     NOT NULL DEFAULT 1 COMMENT '下界是否包含',
    upper_inclusive    TINYINT(1)     NOT NULL DEFAULT 0 COMMENT '上界是否包含',
    score              DECIMAL(18,4)  NOT NULL COMMENT '该区间动态得分',
    order_no           INT            NOT NULL DEFAULT 0 COMMENT '排序号',
    PRIMARY KEY (id),
    KEY idx_rule_dynamic_score_rule (rule_v2_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评分规则动态分（阶段一）';
