-- =====================================================================
-- 事件—字段关联（risk-console-redesign，R4.8）
--
-- 新增独立关联表 event_field，承载"事件—全局字段"多对多关联：
--   - event_type_code 关联事件类型 code
--   - field_id        关联字段库 field_library.id
--   - purposes_json    事件字段用途多选 JSON 数组 [COMPUTE,DECISION]
--   - derived          是否衍生字段标记
-- 以唯一键 uk_event_field(event_type_code, field_id) 保证同一事件下
-- 同一字段不重复关联（R4.4）。
--
-- 既有 derived_field 表（按 eventTypeCode 维度的衍生字段计算定义）语义不同，
-- 本迁移不做任何改动，既有数据保留不变（R4.8）。
--
-- 幂等说明：MySQL 5.7 支持 CREATE TABLE IF NOT EXISTS，故以其保证迁移
-- 重复执行不报错、不丢数据（R14.3）。
-- =====================================================================

CREATE TABLE IF NOT EXISTS event_field (
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    event_type_code VARCHAR(64)  NOT NULL COMMENT '关联事件类型 code',
    field_id        BIGINT       NOT NULL COMMENT '关联字段库 field_library.id',
    purposes_json   VARCHAR(64)  NULL COMMENT '事件字段用途多选 JSON 数组 [COMPUTE,DECISION]',
    derived         TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否衍生字段 0=否 1=是',
    created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_event_field (event_type_code, field_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='事件—字段关联（R4.8）';
