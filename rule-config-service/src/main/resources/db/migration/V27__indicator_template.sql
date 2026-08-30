-- 指标模版：类型与可视化配置快照（用于编辑页回显）
ALTER TABLE indicator_definition
    ADD COLUMN template_type VARCHAR(64) NULL COMMENT '指标模版类型' AFTER status,
    ADD COLUMN template_config JSON NULL COMMENT '模版配置 JSON' AFTER template_type;
