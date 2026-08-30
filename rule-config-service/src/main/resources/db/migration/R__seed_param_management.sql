-- =====================================================================
-- 可重复种子数据 — 参数管理模块（risk-console-redesign，R2.13/R3.8/R4.9/R5.10）
--
-- 预置：业务场景、事件（覆盖维度/事实 + 计算/决策用途）、全局字段库
-- （覆盖 String/Double/Integer/Boolean/Date 多类型）、事件—字段关联（含衍生标记）、
-- 验证策略（含一条不限业务场景的策略）。
--
-- 幂等约定（R15.6）：
--   * 含业务唯一键的表（scenario.uk_scenario_code、event_type.uk_event_type_code、
--     field_library.uk_field_code/uk_field_name、event_field.uk_event_field、
--     scenario_event.uk_scenario_event）使用 INSERT ... ON DUPLICATE KEY UPDATE；
--   * 缺业务唯一键的表（strategy_def 仅按 category 建索引）使用
--     INSERT ... SELECT ... WHERE NOT EXISTS 存在性守卫。
--   重复执行不产生重复记录，可在最新 schema 上反复安全运行。
--
-- 命名中性化（R1.3）：脚本、注释、对象名均使用中性的风控/反欺诈平台命名。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1) 业务场景（≥2）
-- ---------------------------------------------------------------------
INSERT INTO scenario (code, name, status)
VALUES ('SCN_PAYMENT', '支付收单', 'ENABLED'),
       ('SCN_SETTLE',  '资金结算', 'ENABLED')
ON DUPLICATE KEY UPDATE name = VALUES(name), status = VALUES(status);

-- ---------------------------------------------------------------------
-- 2) 事件（≥4，覆盖 DIMENSION/FACT 分型与 COMPUTE/DECISION 用途）
--    scenario_id 通过场景 code 反查归属业务场景。
-- ---------------------------------------------------------------------
INSERT INTO event_type (code, name, status, scenario_id, event_kind, purposes_json)
SELECT t.code, t.name, 1, (SELECT s.id FROM scenario s WHERE s.code = t.scn), t.kind, t.purposes
FROM (
        SELECT 'EVT_PAY_APPLY'        AS code, '支付申请' AS name, 'SCN_PAYMENT' AS scn, 'FACT'      AS kind, '["COMPUTE","DECISION"]' AS purposes
    UNION ALL SELECT 'EVT_PAY_RESULT',       '支付结果', 'SCN_PAYMENT', 'FACT',      '["COMPUTE"]'
    UNION ALL SELECT 'EVT_MERCHANT_PROFILE', '商户画像', 'SCN_PAYMENT', 'DIMENSION', '["COMPUTE"]'
    UNION ALL SELECT 'EVT_SETTLE_APPLY',     '结算申请', 'SCN_SETTLE',  'FACT',      '["DECISION"]'
    UNION ALL SELECT 'EVT_ACCOUNT_DIM',      '账户维度', 'SCN_SETTLE',  'DIMENSION', '["COMPUTE","DECISION"]'
) t
ON DUPLICATE KEY UPDATE name          = VALUES(name),
                        scenario_id   = VALUES(scenario_id),
                        event_kind    = VALUES(event_kind),
                        purposes_json = VALUES(purposes_json);

-- 2b) 场景—事件关联（兼容既有按多对多关联的场景树查询）
INSERT INTO scenario_event (scenario_id, event_type_code)
SELECT s.id, m.evt
FROM (
        SELECT 'SCN_PAYMENT' AS scn, 'EVT_PAY_APPLY'        AS evt
    UNION ALL SELECT 'SCN_PAYMENT', 'EVT_PAY_RESULT'
    UNION ALL SELECT 'SCN_PAYMENT', 'EVT_MERCHANT_PROFILE'
    UNION ALL SELECT 'SCN_SETTLE',  'EVT_SETTLE_APPLY'
    UNION ALL SELECT 'SCN_SETTLE',  'EVT_ACCOUNT_DIM'
) m JOIN scenario s ON s.code = m.scn
ON DUPLICATE KEY UPDATE event_type_code = VALUES(event_type_code);

-- ---------------------------------------------------------------------
-- 3) 全局字段库（≥8，覆盖 STRING/DOUBLE/INTEGER/BOOLEAN/DATE）
-- ---------------------------------------------------------------------
INSERT INTO field_library (code, name, data_type, label, enabled)
SELECT t.code, t.name, t.data_type, t.label, 1
FROM (
        SELECT 'txn_amount'        AS code, '交易金额'         AS name, 'DOUBLE'  AS data_type, '单笔交易金额'      AS label
    UNION ALL SELECT 'txn_currency',      '交易币种',         'STRING',  '交易币种代码'
    UNION ALL SELECT 'merchant_id',       '商户号',           'STRING',  '商户唯一编号'
    UNION ALL SELECT 'txn_count_1d',      '当日交易笔数',     'INTEGER', '当日累计交易笔数'
    UNION ALL SELECT 'is_cross_border',   '是否跨境',         'BOOLEAN', '是否跨境交易'
    UNION ALL SELECT 'account_open_date', '开户日期',         'DATE',    '账户开立日期'
    UNION ALL SELECT 'risk_score',        '风险评分',         'DOUBLE',  '实时风险评分'
    UNION ALL SELECT 'card_bin',          '卡BIN',            'STRING',  '银行卡 BIN 段'
    UNION ALL SELECT 'last_txn_time',     '最近交易时间',     'DATE',    '最近一次交易时间'
    UNION ALL SELECT 'device_age_days',   '设备使用天数',     'INTEGER', '设备首次出现至今天数'
) t
ON DUPLICATE KEY UPDATE name      = VALUES(name),
                        data_type = VALUES(data_type),
                        label     = VALUES(label),
                        enabled   = VALUES(enabled);

-- ---------------------------------------------------------------------
-- 4) 事件—字段关联（含 ≥1 衍生标记 derived=1）
--    field_id 通过字段 code 反查字段库主键。
-- ---------------------------------------------------------------------
INSERT INTO event_field (event_type_code, field_id, purposes_json, derived)
SELECT a.evt, f.id, a.purposes, a.derived
FROM (
        SELECT 'EVT_PAY_APPLY'        AS evt, 'txn_amount'        AS fcode, '["COMPUTE","DECISION"]' AS purposes, 0 AS derived
    UNION ALL SELECT 'EVT_PAY_APPLY',        'txn_currency',      '["DECISION"]',            0
    UNION ALL SELECT 'EVT_PAY_APPLY',        'txn_count_1d',      '["COMPUTE"]',             1  -- 衍生字段（聚合计算）
    UNION ALL SELECT 'EVT_PAY_APPLY',        'is_cross_border',   '["DECISION"]',            0
    UNION ALL SELECT 'EVT_MERCHANT_PROFILE', 'merchant_id',       '["COMPUTE"]',             0
    UNION ALL SELECT 'EVT_MERCHANT_PROFILE', 'risk_score',        '["COMPUTE"]',             1  -- 衍生字段（模型评分）
    UNION ALL SELECT 'EVT_SETTLE_APPLY',     'txn_amount',        '["DECISION"]',            0
    UNION ALL SELECT 'EVT_ACCOUNT_DIM',      'account_open_date', '["COMPUTE"]',             0
) a
JOIN field_library f ON f.code = a.fcode
ON DUPLICATE KEY UPDATE purposes_json = VALUES(purposes_json),
                        derived       = VALUES(derived);

-- ---------------------------------------------------------------------
-- 5) 验证策略（≥3，含一条不限业务场景 any_scope=1）
--    strategy_def 无业务唯一键，使用存在性守卫保证幂等。
-- ---------------------------------------------------------------------
INSERT INTO strategy_def (category, code, name, params_json, status, priority, scope_scenario_id, any_scope)
SELECT 'VERIFY', 'VFY_OTP_SMS', '短信动态码验证', '{"method":"SMS_OTP"}', 'ENABLED', 100,
       (SELECT id FROM scenario WHERE code = 'SCN_PAYMENT'), 0
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM strategy_def WHERE category = 'VERIFY' AND code = 'VFY_OTP_SMS');

INSERT INTO strategy_def (category, code, name, params_json, status, priority, scope_scenario_id, any_scope)
SELECT 'VERIFY', 'VFY_FACE', '人脸识别验证', '{"method":"FACE"}', 'ENABLED', 50,
       (SELECT id FROM scenario WHERE code = 'SCN_SETTLE'), 0
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM strategy_def WHERE category = 'VERIFY' AND code = 'VFY_FACE');

-- 不限业务场景的验证策略（any_scope=1，scope_scenario_id 为空）
INSERT INTO strategy_def (category, code, name, params_json, status, priority, scope_scenario_id, any_scope)
SELECT 'VERIFY', 'VFY_DEVICE', '设备指纹核验', '{"method":"DEVICE_FINGERPRINT"}', 'ENABLED', 200,
       NULL, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM strategy_def WHERE category = 'VERIFY' AND code = 'VFY_DEVICE');
