-- =====================================================================
-- 名单管理重构：名单库 / 维度 / 附加属性 / 库内记录（不绑定黑/白名单类型）
-- =====================================================================

CREATE TABLE list_dimension (
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    code          VARCHAR(64)  NOT NULL COMMENT '维度编码（字段 code）',
    name          VARCHAR(128) NOT NULL COMMENT '维度名称',
    mask_rule     VARCHAR(32)  NULL DEFAULT 'NONE' COMMENT '脱敏规则：NONE/PARTIAL/FULL',
    fuzzy_enabled TINYINT      NOT NULL DEFAULT 0 COMMENT '0=精确 1=支持模糊匹配',
    updated_by    VARCHAR(64)  NULL COMMENT '更新人',
    created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_list_dimension_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='名单维度定义';

CREATE TABLE list_library (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    code        VARCHAR(64)  NOT NULL COMMENT '名单库编码',
    name        VARCHAR(128) NOT NULL COMMENT '名单库名称',
    description VARCHAR(512) NULL COMMENT '说明',
    enabled     TINYINT      NOT NULL DEFAULT 1 COMMENT '0=禁用 1=启用',
    created_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_list_library_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='名单库';

CREATE TABLE list_attr_def (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    code        VARCHAR(64)  NOT NULL COMMENT '属性编码',
    name        VARCHAR(128) NOT NULL COMMENT '属性名称',
    input_type  VARCHAR(32)  NOT NULL DEFAULT 'TEXT' COMMENT 'TEXT/SELECT/DATE/NUMBER',
    required    TINYINT      NOT NULL DEFAULT 0 COMMENT '0=否 1=是',
    multi_value TINYINT      NOT NULL DEFAULT 0 COMMENT '0=否 1=是',
    mask_rule   VARCHAR(32)  NULL DEFAULT 'NONE' COMMENT '脱敏规则',
    created_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_list_attr_def_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='名单附加属性定义';

CREATE TABLE list_entry (
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    library_id      BIGINT       NOT NULL COMMENT '所属名单库',
    dimension_code  VARCHAR(64)  NOT NULL COMMENT '维度编码（key）',
    dimension_value VARCHAR(512) NOT NULL COMMENT '维度值（value）',
    effective_at    DATETIME     NULL COMMENT '生效时间',
    expire_at       DATETIME     NULL COMMENT '失效时间',
    enabled         TINYINT      NOT NULL DEFAULT 1 COMMENT '0=停用 1=启用',
    source          VARCHAR(32)  NOT NULL DEFAULT 'MANUAL' COMMENT '来源：MANUAL/IMPORT/API',
    extra_attrs     JSON         NULL COMMENT '附加属性 JSON',
    created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_list_entry_library (library_id),
    KEY idx_list_entry_dim (library_id, dimension_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='名单库记录';

-- 预置常用维度
INSERT INTO list_dimension (code, name, mask_rule, fuzzy_enabled, updated_by) VALUES
('MechID', '商户编号', 'PARTIAL', 0, 'system'),
('name', '姓名', 'PARTIAL', 1, 'system'),
('country', '国别', 'NONE', 0, 'system'),
('phone', '电话号码', 'PARTIAL', 0, 'system'),
('certno', '企业证件号', 'PARTIAL', 0, 'system'),
('ipaddress', 'ip地址', 'NONE', 0, 'system'),
('acctno', '账号', 'PARTIAL', 0, 'system'),
('idno', '身份证', 'PARTIAL', 0, 'system'),
('location', '地区', 'NONE', 0, 'system'),
('industry', '行业', 'NONE', 0, 'system');
