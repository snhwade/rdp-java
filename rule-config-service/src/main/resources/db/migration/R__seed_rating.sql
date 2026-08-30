-- =====================================================================
-- 可重复种子数据 — 评级模型模块（risk-console-redesign，R10.8）
--
-- 预置：≥2 评级模型，覆盖：
--   * 商户·实时 + 评分定级：subject=MERCHANT, execution_mode=REALTIME, grading_mode=SCORE_BASED
--   * 对私·定时 + 直接定级：subject=INDIVIDUAL, execution_mode=SCHEDULED, grading_mode=DIRECT
-- 每个模型均配置等级区间（rating_grade_band，连续覆盖且互不重叠）与
-- 评级子项/定级项（rating_item），并各建立一个版本快照（rating_model_version）。
--
-- 幂等约定（R15.6）：
--   * rating_model 无业务唯一键，按 name 存在性守卫；
--   * rating_grade_band / rating_item 无业务唯一键，按
--     (rating_model_id, grade) / (rating_model_id, sub_item|grade) 守卫；
--   * rating_model_version 含唯一键 uk_rmv_model_version(rating_model_id, version)，
--     使用 INSERT ... ON DUPLICATE KEY UPDATE。
--   重复执行不产生重复记录。
--
-- 依赖：引用的事件 code 由 R__seed_param_management.sql 预置。
-- 命名中性化（R1.3）：脚本、注释、对象名均使用中性命名。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1) 评级模型（商户·实时·评分定级）
-- ---------------------------------------------------------------------
INSERT INTO rating_model (name, event_type_code, execution_mode, subject, grading_mode, status, version)
SELECT '商户实时风险评级', 'EVT_PAY_APPLY', 'REALTIME', 'MERCHANT', 'SCORE_BASED', 'ONLINE', 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM rating_model WHERE name = '商户实时风险评级');

-- 1a) 等级区间（连续覆盖 0..100，互不重叠）
INSERT INTO rating_grade_band (rating_model_id, min_score, max_score, grade, order_no)
SELECT m.id, b.min_score, b.max_score, b.grade, b.order_no
FROM rating_model m
JOIN (
        SELECT 0   AS min_score, 40  AS max_score, 'LOW'  AS grade, 1 AS order_no
    UNION ALL SELECT 40,  70,  'MID',  2
    UNION ALL SELECT 70,  100, 'HIGH', 3
) b ON m.name = '商户实时风险评级'
WHERE NOT EXISTS (
    SELECT 1 FROM rating_grade_band g WHERE g.rating_model_id = m.id AND g.grade = b.grade
);

-- 1b) 评级子项（score + sub_item_cap，评分定级）
INSERT INTO rating_item (rating_model_id, category, sub_item, condition_expr, score, sub_item_cap, importance)
SELECT m.id, i.category, i.sub_item, i.condition_expr, i.score, i.sub_item_cap, i.importance
FROM rating_model m
JOIN (
        SELECT '交易行为' AS category, '大额交易' AS sub_item, 'txn_amount > 50000' AS condition_expr, 30 AS score, 50 AS sub_item_cap, 'HIGH' AS importance
    UNION ALL SELECT '交易行为', '高频交易', 'txn_count_1d > 20', 25, 50, 'MID'
    UNION ALL SELECT '设备风险', '新设备',   'device_age_days < 7', 20, 30, 'MID'
) i ON m.name = '商户实时风险评级'
WHERE NOT EXISTS (
    SELECT 1 FROM rating_item r WHERE r.rating_model_id = m.id AND r.sub_item = i.sub_item
);

-- 1c) 版本快照
INSERT INTO rating_model_version (rating_model_id, version, snapshot_json, created_by)
SELECT m.id, 1,
       '{"name":"商户实时风险评级","executionMode":"REALTIME","subject":"MERCHANT","gradingMode":"SCORE_BASED","gradeBands":[{"min":0,"max":40,"grade":"LOW"},{"min":40,"max":70,"grade":"MID"},{"min":70,"max":100,"grade":"HIGH"}]}',
       'seed'
FROM rating_model m
WHERE m.name = '商户实时风险评级'
ON DUPLICATE KEY UPDATE snapshot_json = VALUES(snapshot_json);

-- ---------------------------------------------------------------------
-- 2) 评级模型（对私·定时·直接定级）
-- ---------------------------------------------------------------------
INSERT INTO rating_model (name, event_type_code, execution_mode, subject, grading_mode, status, version)
SELECT '对私定时风险评级', 'EVT_ACCOUNT_DIM', 'SCHEDULED', 'INDIVIDUAL', 'DIRECT', 'ONLINE', 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM rating_model WHERE name = '对私定时风险评级');

-- 2a) 等级区间（直接定级亦定义等级序，order_no 用于等级高低比较）
INSERT INTO rating_grade_band (rating_model_id, min_score, max_score, grade, order_no)
SELECT m.id, b.min_score, b.max_score, b.grade, b.order_no
FROM rating_model m
JOIN (
        SELECT 0  AS min_score, 1  AS max_score, 'A' AS grade, 1 AS order_no
    UNION ALL SELECT 1, 2, 'B', 2
    UNION ALL SELECT 2, 3, 'C', 3
) b ON m.name = '对私定时风险评级'
WHERE NOT EXISTS (
    SELECT 1 FROM rating_grade_band g WHERE g.rating_model_id = m.id AND g.grade = b.grade
);

-- 2b) 定级项（grade，直接定级）
INSERT INTO rating_item (rating_model_id, category, sub_item, condition_expr, grade, importance)
SELECT m.id, i.category, i.sub_item, i.condition_expr, i.grade, i.importance
FROM rating_model m
JOIN (
        SELECT '账户风险' AS category, '跨境账户' AS sub_item, 'is_cross_border == true' AS condition_expr, 'C' AS grade, 'HIGH' AS importance
    UNION ALL SELECT '账户风险', '高分账户', 'risk_score > 80', 'B', 'MID'
    UNION ALL SELECT '账户风险', '普通账户', 'risk_score <= 80', 'A', 'LOW'
) i ON m.name = '对私定时风险评级'
WHERE NOT EXISTS (
    SELECT 1 FROM rating_item r WHERE r.rating_model_id = m.id AND r.sub_item = i.sub_item
);

-- 2c) 版本快照
INSERT INTO rating_model_version (rating_model_id, version, snapshot_json, created_by)
SELECT m.id, 1,
       '{"name":"对私定时风险评级","executionMode":"SCHEDULED","subject":"INDIVIDUAL","gradingMode":"DIRECT","grades":["A","B","C"]}',
       'seed'
FROM rating_model m
WHERE m.name = '对私定时风险评级'
ON DUPLICATE KEY UPDATE snapshot_json = VALUES(snapshot_json);
