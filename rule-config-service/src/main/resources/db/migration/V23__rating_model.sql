-- =====================================================================
-- 评级模型（risk-console-redesign，R10.1/R11.1/R12.1/R13.1/R14.3）
--
-- 新增评级模型子域的持久化表，承载"模型管理 + 可视化等级区间 +
-- 评分定级/直接定级配置 + 版本快照"：
--   - rating_model         评级模型聚合根（事件归属/执行方式/评级主体/
--                          定级方式/状态/版本）
--   - rating_grade_band    可视化等级区间 {minScore,maxScore,grade,orderNo}
--   - rating_item          评级子项（评分定级 score/subItemCap）
--                          与定级项（直接定级 grade）合表承载
--   - rating_model_version 版本快照（含等级区间与定级配置的 JSON 快照）
--
-- 枚举语义：
--   execution_mode  REALTIME（实时）/SCHEDULED（定时）
--   subject         MERCHANT（商户/对公）/INDIVIDUAL（对私）
--   grading_mode    SCORE_BASED（评分定级）/DIRECT（直接定级）
--   status          ONLINE（上线）/OFFLINE（下线，默认）
--
-- 幂等说明：MySQL 5.7 支持 CREATE TABLE IF NOT EXISTS，故以其保证迁移
-- 重复执行不报错、不丢数据；既有数据完整保留（R14.3）。
-- =====================================================================

-- 评级模型聚合根（R10.1）
CREATE TABLE IF NOT EXISTS rating_model (
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    name            VARCHAR(128) NOT NULL COMMENT '评级模型名称',
    event_type_code VARCHAR(64)  NOT NULL COMMENT '所属事件类型 code',
    execution_mode  VARCHAR(16)  NOT NULL COMMENT '执行方式 REALTIME/SCHEDULED',
    subject         VARCHAR(16)  NOT NULL COMMENT '评级主体 MERCHANT/INDIVIDUAL',
    grading_mode    VARCHAR(16)  NOT NULL COMMENT '定级方式 SCORE_BASED/DIRECT',
    status          VARCHAR(16)  NOT NULL DEFAULT 'OFFLINE' COMMENT '上下线状态 ONLINE/OFFLINE',
    version         INT          NOT NULL DEFAULT 1 COMMENT '当前版本号',
    created_by      VARCHAR(64)  NULL COMMENT '创建人',
    updated_by      VARCHAR(64)  NULL COMMENT '更新人',
    created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_rating_model_event (event_type_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评级模型聚合根（R10.1）';

-- 可视化等级区间（R11.1）
CREATE TABLE IF NOT EXISTS rating_grade_band (
    id              BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键',
    rating_model_id BIGINT         NOT NULL COMMENT '所属评级模型ID',
    min_score       DECIMAL(18,4)  NOT NULL COMMENT '区间下界（含）',
    max_score       DECIMAL(18,4)  NOT NULL COMMENT '区间上界（含）',
    grade           VARCHAR(64)    NOT NULL COMMENT '区间对应等级',
    order_no        INT            NOT NULL DEFAULT 0 COMMENT '等级序（用于等级高低比较）',
    PRIMARY KEY (id),
    KEY idx_rgb_model (rating_model_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评级模型等级区间（R11.1）';

-- 评级子项 / 定级项（R12.1/R13.1）
CREATE TABLE IF NOT EXISTS rating_item (
    id              BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键',
    rating_model_id BIGINT         NOT NULL COMMENT '所属评级模型ID',
    category        VARCHAR(128)   NULL COMMENT '评级类别',
    sub_item        VARCHAR(128)   NULL COMMENT '评级子项名称',
    condition_expr  LONGTEXT       NULL COMMENT '命中条件表达式',
    score           DECIMAL(18,4)  NULL COMMENT '评分定级：子项计入分值',
    sub_item_cap    DECIMAL(18,4)  NULL COMMENT '评分定级：子项分值上限',
    importance      VARCHAR(32)    NULL COMMENT '重要度',
    grade           VARCHAR(64)    NULL COMMENT '直接定级：命中等级',
    PRIMARY KEY (id),
    KEY idx_rating_item_model (rating_model_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评级子项/定级项（R12.1/R13.1）';

-- 评级模型版本快照（R10.6/R11.5/R12.1/R13.1）
CREATE TABLE IF NOT EXISTS rating_model_version (
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    rating_model_id BIGINT       NOT NULL COMMENT '所属评级模型ID',
    version         INT          NOT NULL COMMENT '版本号',
    snapshot_json   LONGTEXT     NOT NULL COMMENT '版本快照 JSON（含等级区间与定级配置）',
    created_by      VARCHAR(64)  NULL COMMENT '创建人',
    created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_rmv_model (rating_model_id),
    UNIQUE KEY uk_rmv_model_version (rating_model_id, version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评级模型版本快照（R10.6）';
