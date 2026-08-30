-- =====================================================================
-- S5 复核审批中心（Maker-Checker）：配置变更草稿 + 审批闭环
-- =====================================================================
CREATE TABLE approval_request (
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    asset_type    VARCHAR(32)  NOT NULL COMMENT '资产类型 RULE/INDICATOR/LIST/DECISION_TABLE/SCORECARD/DECISION_FLOW',
    op_type       VARCHAR(16)  NOT NULL COMMENT '操作 CREATE/UPDATE/DELETE',
    target_id     VARCHAR(64)  NULL COMMENT '目标资产标识（更新/删除时）',
    payload       MEDIUMTEXT   NULL COMMENT '变更内容(JSON)',
    status        VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/APPROVED/REJECTED',
    applicant     VARCHAR(64)  NOT NULL COMMENT '发起人',
    apply_time    DATETIME(3)  NOT NULL COMMENT '发起时间',
    approver      VARCHAR(64)  NULL COMMENT '审批人',
    approve_time  DATETIME(3)  NULL COMMENT '审批时间',
    reject_reason VARCHAR(512) NULL COMMENT '驳回原因',
    created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_approval_status (status),
    KEY idx_approval_applicant (applicant)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='复核审批申请（S5）';
