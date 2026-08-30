-- =====================================================================
-- 规则包启用快照（P2）：每次启用时落一条快照，支持回退到上一启用快照
-- =====================================================================
CREATE TABLE IF NOT EXISTS rule_package_enabled_snapshot (
    id               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    rule_package_id  BIGINT       NOT NULL COMMENT '规则包ID',
    version          INT          NOT NULL COMMENT '包内递增版本号（从1起）',
    snapshot_json    LONGTEXT     NOT NULL COMMENT '启用时整包快照(JSON)',
    created_by       VARCHAR(64)  NULL COMMENT '创建人',
    created_at       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_rpes_pkg_ver (rule_package_id, version),
    KEY idx_rpes_pkg (rule_package_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='规则包启用快照';
