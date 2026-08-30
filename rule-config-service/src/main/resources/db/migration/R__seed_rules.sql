-- =====================================================================
-- 可重复种子数据 — 规则管理模块（risk-console-redesign，R6.7/R7.9）
--
-- 预置：≥2 规则包（命中模式 + 评分模式各一），其下规则覆盖三态
-- （ONLINE 上线 / TRIAL_RUN 试运行 / OFFLINE 下线），并建立规则包—规则关联。
--
-- 幂等约定（R15.6）：
--   * rule_package 含业务唯一键 uk_rule_package_mode_code(trigger_mode, code)，
--     使用 INSERT ... ON DUPLICATE KEY UPDATE；
--   * rule_v2 仅有主键与普通索引（无业务唯一键），使用
--     INSERT ... SELECT ... WHERE NOT EXISTS（按 code 守卫）保证幂等；
--   * rule_package_rule 含唯一键 uk_rule_package_rule(rule_package_id, rule_v2_id)，
--     使用 INSERT ... ON DUPLICATE KEY UPDATE。
--   重复执行不产生重复记录。
--
-- 依赖：本脚本引用的事件 code 由 R__seed_param_management.sql 预置。
-- 可重复迁移按文件名字典序执行（param_management < rules），依赖先行可用。
--
-- 命名中性化（R1.3）：脚本、注释、对象名均使用中性命名。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1) 规则包（≥2）：命中模式 PKG_PAY_HIT、评分模式 PKG_PAY_SCORE
-- ---------------------------------------------------------------------
INSERT INTO rule_package (code, name, trigger_mode, compute_mode, status, version)
VALUES ('PKG_PAY_HIT',   '支付命中规则包', 'HIT',   'ONLINE', 'ENABLED', 1),
       ('PKG_PAY_SCORE', '支付评分规则包', 'SCORE', 'ONLINE', 'ENABLED', 1)
ON DUPLICATE KEY UPDATE name = VALUES(name), status = VALUES(status);

-- 规则包—决策事件关联
INSERT INTO rule_package_event (rule_package_id, event_type_code)
SELECT p.id, m.evt
FROM (
        SELECT 'PKG_PAY_HIT'   AS pcode, 'HIT'   AS pmode, 'EVT_PAY_APPLY' AS evt
    UNION ALL SELECT 'PKG_PAY_SCORE', 'SCORE', 'EVT_PAY_APPLY'
) m JOIN rule_package p ON p.code = m.pcode AND p.trigger_mode = m.pmode
ON DUPLICATE KEY UPDATE event_type_code = VALUES(event_type_code);

-- ---------------------------------------------------------------------
-- 2) 规则（覆盖三态 ONLINE/TRIAL_RUN/OFFLINE）
--    rule_v2 无业务唯一键，按 code 存在性守卫保证幂等。
--    condition_json / compiled_expr 填充有效内容（非空），保证规则可被引擎加载执行。
-- ---------------------------------------------------------------------

-- 命中包：上线规则
INSERT INTO rule_v2 (code, name, rule_package_id, rule_kind, event_type_code, risk_level_code,
                     condition_json, compiled_expr, priority, status, version)
SELECT 'RULE_PAY_BIGAMT', '大额支付拦截',
       (SELECT id FROM rule_package WHERE code = 'PKG_PAY_HIT' AND trigger_mode = 'HIT'),
       'HIT', 'EVT_PAY_APPLY', 'HIGH',
       '{"op":"LEAF","left":{"source":"FIELD","ref":"txn_amount","dataType":"NUMBER"},"operator":"GT","right":{"kind":"CONST","value":50000}}', 'txn_amount > 50000', 100, 'ONLINE', 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM rule_v2 WHERE code = 'RULE_PAY_BIGAMT');

-- 命中包：试运行规则
INSERT INTO rule_v2 (code, name, rule_package_id, rule_kind, event_type_code, risk_level_code,
                     condition_json, compiled_expr, priority, status, version)
SELECT 'RULE_PAY_CROSSBORDER', '跨境支付观察',
       (SELECT id FROM rule_package WHERE code = 'PKG_PAY_HIT' AND trigger_mode = 'HIT'),
       'HIT', 'EVT_PAY_APPLY', 'MID',
       '{"op":"LEAF","left":{"source":"FIELD","ref":"is_cross_border","dataType":"BOOLEAN"},"operator":"EQ","right":{"kind":"CONST","value":true}}', 'is_cross_border == true', 90, 'TRIAL_RUN', 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM rule_v2 WHERE code = 'RULE_PAY_CROSSBORDER');

-- 命中包：下线规则
INSERT INTO rule_v2 (code, name, rule_package_id, rule_kind, event_type_code, risk_level_code,
                     condition_json, compiled_expr, priority, status, version)
SELECT 'RULE_PAY_LEGACY', '历史风控规则（停用）',
       (SELECT id FROM rule_package WHERE code = 'PKG_PAY_HIT' AND trigger_mode = 'HIT'),
       'HIT', 'EVT_PAY_APPLY', 'LOW',
       '{"op":"LEAF","left":{"source":"FIELD","ref":"device_age_days","dataType":"NUMBER"},"operator":"LT","right":{"kind":"CONST","value":1}}', 'device_age_days < 1', 10, 'OFFLINE', 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM rule_v2 WHERE code = 'RULE_PAY_LEGACY');

-- 评分包：上线评分规则
INSERT INTO rule_v2 (code, name, rule_package_id, rule_kind, event_type_code, risk_type_code,
                     base_score, condition_json, compiled_expr, priority, status, version)
SELECT 'RULE_SCORE_HIGHFREQ', '高频交易加分',
       (SELECT id FROM rule_package WHERE code = 'PKG_PAY_SCORE' AND trigger_mode = 'SCORE'),
       'SCORE', 'EVT_PAY_APPLY', 'FRAUD',
       30, '{"op":"LEAF","left":{"source":"FIELD","ref":"txn_count_1d","dataType":"NUMBER"},"operator":"GT","right":{"kind":"CONST","value":20}}', 'txn_count_1d > 20', 100, 'ONLINE', 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM rule_v2 WHERE code = 'RULE_SCORE_HIGHFREQ');

-- 评分包：试运行评分规则
INSERT INTO rule_v2 (code, name, rule_package_id, rule_kind, event_type_code, risk_type_code,
                     base_score, condition_json, compiled_expr, priority, status, version)
SELECT 'RULE_SCORE_NEWDEVICE', '新设备加分观察',
       (SELECT id FROM rule_package WHERE code = 'PKG_PAY_SCORE' AND trigger_mode = 'SCORE'),
       'SCORE', 'EVT_PAY_APPLY', 'FRAUD',
       15, '{"op":"LEAF","left":{"source":"FIELD","ref":"device_age_days","dataType":"NUMBER"},"operator":"LT","right":{"kind":"CONST","value":7}}', 'device_age_days < 7', 80, 'TRIAL_RUN', 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM rule_v2 WHERE code = 'RULE_SCORE_NEWDEVICE');

-- ---------------------------------------------------------------------
-- 3) 规则包—规则关联（幂等，唯一键 uk_rule_package_rule）
-- ---------------------------------------------------------------------
INSERT INTO rule_package_rule (rule_package_id, rule_v2_id, priority)
SELECT r.rule_package_id, r.id, r.priority
FROM rule_v2 r
WHERE r.code IN ('RULE_PAY_BIGAMT', 'RULE_PAY_CROSSBORDER', 'RULE_PAY_LEGACY',
                 'RULE_SCORE_HIGHFREQ', 'RULE_SCORE_NEWDEVICE')
  AND r.rule_package_id IS NOT NULL
ON DUPLICATE KEY UPDATE priority = VALUES(priority);
