-- =====================================================================
-- S1 名单管理增强：名单记录表（黑/白/关注名单）
-- =====================================================================
CREATE TABLE list_record (
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    list_type       VARCHAR(16)  NOT NULL COMMENT '名单类型 BLACK/WHITE/WATCH',
    dimension       VARCHAR(64)  NOT NULL COMMENT '维度字段名（如 merchantId/idNo/subjectName）',
    dimension_value VARCHAR(512) NOT NULL COMMENT '维度值',
    reason          VARCHAR(512) NULL COMMENT '加入名单原因',
    immune_rule_id  BIGINT       NULL COMMENT '白名单免疫的规则 id（null=对所有规则免疫）',
    expire_at       DATETIME     NULL COMMENT '到期时间（null=长期有效）',
    enabled         TINYINT      NOT NULL DEFAULT 1 COMMENT '0=禁用 1=启用',
    created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_list_dim_value (dimension, dimension_value),
    KEY idx_list_type (list_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='名单记录（S1）';
