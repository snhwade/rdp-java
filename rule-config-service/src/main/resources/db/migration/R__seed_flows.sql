-- =====================================================================
-- 可重复种子数据 — 决策流模块（risk-console-redesign，R8.9）
--
-- 预置：支付申请事件决策流 = START -> 规则包(PKG_PAY_HIT) -> END
-- （无 endDecision 时由规则命中聚合产出最终决策，演示决策流调用）。
--
-- 幂等：按 name + event_type_code 守卫，不重复插入。
-- 依赖：R__seed_rules.sql（规则包 PKG_PAY_HIT）、R__seed_param_management.sql（事件）。
-- =====================================================================

INSERT INTO decision_flow (name, event_type_code, nodes_json, edges_json, start_node_id, status)
SELECT '支付决策流', 'EVT_PAY_APPLY',
       CONCAT(
           '[{"nodeId":"start","type":"START","refType":null,"refId":null,"config":null},',
           '{"nodeId":"rp_pay_hit","type":"RULE_PACKAGE","refType":"RULE_PACKAGE","refId":',
           (SELECT id FROM rule_package WHERE code = 'PKG_PAY_HIT' AND trigger_mode = 'HIT' LIMIT 1),
           ',"config":null},',
           '{"nodeId":"end","type":"END","refType":null,"refId":null,"config":null}]'
       ),
       CONCAT(
           '[{"from":"start","to":"rp_pay_hit","condition":null,"trafficPercent":null,"isDefault":false},',
           '{"from":"rp_pay_hit","to":"end","condition":null,"trafficPercent":null,"isDefault":false}]'
       ),
       'start', 'ENABLED'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM decision_flow WHERE name = '支付决策流' AND event_type_code = 'EVT_PAY_APPLY'
);

INSERT INTO decision_flow_version (decision_flow_id, version, snapshot_json, status, created_by)
SELECT df.id, 1,
       CONCAT('{"name":"支付决策流","eventTypeCode":"EVT_PAY_APPLY","startNodeId":"start","status":"ONLINE",',
              '"nodes":', df.nodes_json, ',"edges":', df.edges_json, '}'),
       'ONLINE', 'seed'
FROM decision_flow df
WHERE df.name = '支付决策流' AND df.event_type_code = 'EVT_PAY_APPLY'
  AND NOT EXISTS (
        SELECT 1 FROM decision_flow_version v
        WHERE v.decision_flow_id = df.id AND v.version = 1
  );

-- 将已存在的「支付决策流」升级为含规则包节点的演示流（可重复执行）
UPDATE decision_flow df
SET nodes_json = CONCAT(
        '[{"nodeId":"start","type":"START","refType":null,"refId":null,"config":null},',
        '{"nodeId":"rp_pay_hit","type":"RULE_PACKAGE","refType":"RULE_PACKAGE","refId":',
        (SELECT id FROM rule_package WHERE code = 'PKG_PAY_HIT' AND trigger_mode = 'HIT' LIMIT 1),
        ',"config":null},',
        '{"nodeId":"end","type":"END","refType":null,"refId":null,"config":null}]'
    ),
    edges_json = CONCAT(
        '[{"from":"start","to":"rp_pay_hit","condition":null,"trafficPercent":null,"isDefault":false},',
        '{"from":"rp_pay_hit","to":"end","condition":null,"trafficPercent":null,"isDefault":false}]'
    ),
    start_node_id = 'start'
WHERE df.name = '支付决策流' AND df.event_type_code = 'EVT_PAY_APPLY';

UPDATE decision_flow_version v
JOIN decision_flow df ON df.id = v.decision_flow_id
SET v.snapshot_json = CONCAT('{"name":"支付决策流","eventTypeCode":"EVT_PAY_APPLY","startNodeId":"start","status":"ONLINE",',
                             '"nodes":', df.nodes_json, ',"edges":', df.edges_json, '}')
WHERE df.name = '支付决策流' AND df.event_type_code = 'EVT_PAY_APPLY' AND v.status = 'ONLINE';
