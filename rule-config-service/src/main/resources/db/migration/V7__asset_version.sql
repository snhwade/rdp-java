-- =====================================================================
-- S6 资产生命周期：资产版本快照（规则/决策表/评分卡/决策流）
-- =====================================================================
CREATE TABLE asset_version (
    id          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    asset_type  VARCHAR(32) NOT NULL COMMENT '资产类型',
    asset_id    VARCHAR(64) NOT NULL COMMENT '资产标识',
    version     INT         NOT NULL COMMENT '版本号（该资产内递增）',
    content     MEDIUMTEXT  NULL COMMENT '内容快照(JSON)',
    operator    VARCHAR(64) NULL COMMENT '操作人',
    created_at  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_asset_version (asset_type, asset_id, version),
    KEY idx_asset_version_asset (asset_type, asset_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='资产版本快照（S6）';
