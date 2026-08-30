-- 指标定义：绑定事件、上下线状态、展示名称（指标管理优化）
ALTER TABLE indicator_definition
    ADD COLUMN name VARCHAR(128) NULL COMMENT '指标名称' AFTER ref_name,
    ADD COLUMN description TEXT NULL COMMENT '指标描述' AFTER name,
    ADD COLUMN event_type_codes JSON NULL COMMENT '绑定的事件类型 code 列表' AFTER description,
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'OFFLINE' COMMENT 'ONLINE/OFFLINE' AFTER event_type_codes;

UPDATE indicator_definition SET event_type_codes = JSON_ARRAY() WHERE event_type_codes IS NULL;
ALTER TABLE indicator_definition MODIFY COLUMN event_type_codes JSON NOT NULL COMMENT '绑定的事件类型 code 列表';
