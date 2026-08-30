-- 指标分组：一个分组可绑定多个事件，其下挂多个指标定义
CREATE TABLE indicator_group (
    id                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    name              VARCHAR(128) NOT NULL COMMENT '分组名称',
    org_name          VARCHAR(64)  NOT NULL DEFAULT '总部' COMMENT '归属机构',
    event_type_codes  JSON         NOT NULL COMMENT '绑定事件 code 列表',
    description       TEXT         NULL COMMENT '描述',
    created_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_indicator_group_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='指标分组';

ALTER TABLE indicator_definition
    ADD COLUMN group_id BIGINT NULL COMMENT '所属指标分组' AFTER id,
    ADD KEY idx_indicator_definition_group (group_id);
