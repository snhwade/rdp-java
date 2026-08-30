-- =====================================================================
-- S7 字段库与衍生字段
-- =====================================================================
CREATE TABLE field_library (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    name        VARCHAR(64)  NOT NULL COMMENT '字段名',
    data_type   VARCHAR(16)  NOT NULL COMMENT '类型 LONG/DOUBLE/STRING/BOOLEAN/DATE',
    label       VARCHAR(128) NULL COMMENT '含义说明',
    enabled     TINYINT      NOT NULL DEFAULT 1 COMMENT '0=禁用 1=启用',
    created_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_field_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='字段库（S7）';

CREATE TABLE derived_field (
    id               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    event_type_code  VARCHAR(64)  NOT NULL COMMENT '关联事件类型',
    name             VARCHAR(64)  NOT NULL COMMENT '衍生字段名',
    expression       VARCHAR(2000) NOT NULL COMMENT 'Aviator 表达式',
    enabled          TINYINT      NOT NULL DEFAULT 1 COMMENT '0=禁用 1=启用',
    created_at       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_derived_event_type (event_type_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='衍生字段（S7）';
