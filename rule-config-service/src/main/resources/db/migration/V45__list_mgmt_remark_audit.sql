-- 名单管理 LD1/LX1：库/条目备注 + 导入/同步审计
ALTER TABLE list_library
    ADD COLUMN remark VARCHAR(512) NULL COMMENT '备注（人工说明）' AFTER description;

ALTER TABLE list_entry
    ADD COLUMN remark VARCHAR(512) NULL COMMENT '备注（人工说明）' AFTER source;

CREATE TABLE list_import_audit (
    id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    library_id   BIGINT       NOT NULL COMMENT '名单库ID',
    source       VARCHAR(64)  NOT NULL COMMENT '来源标识（如 MANUAL_IMPORT/EXTERNAL_STUB）',
    batch_id     VARCHAR(64)  NULL COMMENT '批次号',
    entry_count  INT          NOT NULL DEFAULT 0 COMMENT '本批次条数',
    status       VARCHAR(32)  NOT NULL COMMENT 'STUB_RECORDED/COMPLETED/FAILED',
    message      VARCHAR(512) NULL COMMENT '说明',
    created_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_list_import_library (library_id),
    KEY idx_list_import_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='名单导入/同步审计（LX1）';
