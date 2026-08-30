-- 移除未接线的 V1 规则组 / 选择器 / 决策优先级配置表（已由规则包 + 验证策略替代）。
DROP TABLE IF EXISTS rule_group_rule;
DROP TABLE IF EXISTS rule_selector;
DROP TABLE IF EXISTS rule_group;
DROP TABLE IF EXISTS decision_priority_config;
