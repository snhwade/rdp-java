-- =====================================================================
-- 产品对齐增强 - 阶段三支撑维度表
-- 场景 / 机构树 / 风险类型 / 风险等级 / 决策标签 / 枚举库 / 枚举值
-- Requirements: 10.1, 11.1, 12.1, 12.2
-- =====================================================================

-- ---------------------------------------------------------------------
-- 场景（Scenario）
-- ---------------------------------------------------------------------
CREATE TABLE scenario (
    id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    code         VARCHAR(64)  NOT NULL COMMENT '场景编码（唯一）',
    name         VARCHAR(128) NOT NULL COMMENT '场景名称',
    status       VARCHAR(16)  NOT NULL DEFAULT 'ENABLED' COMMENT '状态 ENABLED/DISABLED',
    create_user  VARCHAR(64)  NULL COMMENT '创建人',
    create_time  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_user  VARCHAR(64)  NULL COMMENT '更新人',
    update_time  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_scenario_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='场景（阶段三）';

-- ---------------------------------------------------------------------
-- 场景-事件关联（多对多）
-- ---------------------------------------------------------------------
CREATE TABLE scenario_event (
    id               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    scenario_id      BIGINT       NOT NULL COMMENT '场景ID',
    event_type_code  VARCHAR(64)  NOT NULL COMMENT '事件类型编码',
    PRIMARY KEY (id),
    UNIQUE KEY uk_scenario_event (scenario_id, event_type_code),
    KEY idx_scenario_event_scenario (scenario_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='场景-事件关联（阶段三）';

-- ---------------------------------------------------------------------
-- 机构树（Org）含物化路径 path
-- ---------------------------------------------------------------------
CREATE TABLE org (
    id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    code         VARCHAR(64)  NOT NULL COMMENT '机构编码（唯一）',
    name         VARCHAR(128) NOT NULL COMMENT '机构名称',
    parent_id    BIGINT       NULL COMMENT '父机构ID（NULL=根机构）',
    path         VARCHAR(512) NOT NULL COMMENT '物化路径，如 /1/4/',
    status       VARCHAR(16)  NOT NULL DEFAULT 'ENABLED' COMMENT '状态 ENABLED/DISABLED',
    create_user  VARCHAR(64)  NULL COMMENT '创建人',
    create_time  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_user  VARCHAR(64)  NULL COMMENT '更新人',
    update_time  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_org_code (code),
    KEY idx_org_parent (parent_id),
    KEY idx_org_path (path)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='机构树（阶段三）';

-- ---------------------------------------------------------------------
-- 风险类型字典（risk_type）
-- ---------------------------------------------------------------------
CREATE TABLE risk_type (
    id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    code         VARCHAR(64)  NOT NULL COMMENT '风险类型编码（唯一）',
    name         VARCHAR(128) NOT NULL COMMENT '风险类型名称',
    status       VARCHAR(16)  NOT NULL DEFAULT 'ENABLED' COMMENT '状态 ENABLED/DISABLED',
    create_user  VARCHAR(64)  NULL COMMENT '创建人',
    create_time  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_user  VARCHAR(64)  NULL COMMENT '更新人',
    update_time  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_risk_type_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='风险类型字典（阶段三）';

-- ---------------------------------------------------------------------
-- 风险等级字典（risk_level）
-- ---------------------------------------------------------------------
CREATE TABLE risk_level (
    id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    code         VARCHAR(64)  NOT NULL COMMENT '风险等级编码（唯一）',
    name         VARCHAR(128) NOT NULL COMMENT '风险等级名称',
    order_no     INT          NOT NULL DEFAULT 0 COMMENT '排序号',
    status       VARCHAR(16)  NOT NULL DEFAULT 'ENABLED' COMMENT '状态 ENABLED/DISABLED',
    create_user  VARCHAR(64)  NULL COMMENT '创建人',
    create_time  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_user  VARCHAR(64)  NULL COMMENT '更新人',
    update_time  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_risk_level_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='风险等级字典（阶段三）';

-- ---------------------------------------------------------------------
-- 决策标签字典（decision_tag）
-- ---------------------------------------------------------------------
CREATE TABLE decision_tag (
    id                    BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    code                  VARCHAR(64)  NOT NULL COMMENT '决策标签编码（唯一）',
    name                  VARCHAR(128) NOT NULL COMMENT '决策标签名称',
    applicable_asset_type VARCHAR(32)  NULL COMMENT '适用资产类型',
    status                VARCHAR(16)  NOT NULL DEFAULT 'ENABLED' COMMENT '状态 ENABLED/DISABLED',
    create_user           VARCHAR(64)  NULL COMMENT '创建人',
    create_time           DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_user           VARCHAR(64)  NULL COMMENT '更新人',
    update_time           DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_decision_tag_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='决策标签字典（阶段三）';

-- ---------------------------------------------------------------------
-- 枚举库（enum_lib）
-- ---------------------------------------------------------------------
CREATE TABLE enum_lib (
    id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    code         VARCHAR(64)  NOT NULL COMMENT '枚举库编码（唯一）',
    name         VARCHAR(128) NOT NULL COMMENT '枚举库名称',
    data_type    VARCHAR(16)  NOT NULL DEFAULT 'STRING' COMMENT '数据类型 STRING/LONG/DOUBLE/BOOLEAN',
    status       VARCHAR(16)  NOT NULL DEFAULT 'ENABLED' COMMENT '状态 ENABLED/DISABLED',
    create_user  VARCHAR(64)  NULL COMMENT '创建人',
    create_time  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_user  VARCHAR(64)  NULL COMMENT '更新人',
    update_time  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_enum_lib_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='枚举库（阶段三）';

-- ---------------------------------------------------------------------
-- 枚举值（enum_value）
-- ---------------------------------------------------------------------
CREATE TABLE enum_value (
    id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    enum_lib_id  BIGINT       NOT NULL COMMENT '枚举库ID',
    value        VARCHAR(256) NOT NULL COMMENT '枚举值',
    label        VARCHAR(256) NULL COMMENT '显示标签',
    order_no     INT          NOT NULL DEFAULT 0 COMMENT '排序号',
    PRIMARY KEY (id),
    UNIQUE KEY uk_enum_value (enum_lib_id, value),
    KEY idx_enum_value_lib (enum_lib_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='枚举值（阶段三）';
