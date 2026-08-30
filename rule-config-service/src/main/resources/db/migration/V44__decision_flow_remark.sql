-- 决策流备注（D1）+ 上一启用版本指针（R1）
ALTER TABLE decision_flow
    ADD COLUMN remark VARCHAR(512) NULL COMMENT '备注（人工说明）' AFTER status,
    ADD COLUMN prev_online_version INT NULL COMMENT '回退用：上一启用版本号' AFTER remark;
