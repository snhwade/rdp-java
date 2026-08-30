-- 默认 Agent 策略：LLM 改为国内 DeepSeek（OpenAI 兼容）；编排模式保持 ORCHESTRATED
UPDATE agent_strategy
SET config_json = '{
  "llmMode": "ORCHESTRATED",
  "maxOrchestrationSteps": 8,
  "featureFields": ["merchantId", "amount", "eventTypeCode", "country"],
  "knownRisks": [
    {"id": "blacklist", "name": "黑名单命中", "description": "主体在精确黑名单", "signalKeys": ["blackHit"], "suggestTools": ["list_check"]},
    {"id": "watchlist", "name": "关注名单", "description": "主体在关注名单", "signalKeys": ["watchHit"], "suggestTools": ["list_check"]},
    {"id": "amount_spike", "name": "突发巨额", "description": "单笔金额相对历史异常", "signalKeys": ["amountSpike"], "suggestTools": ["analyze_amount_spike"]},
    {"id": "engine_reject", "name": "引擎拒绝", "description": "规则引擎已给出拒绝", "signalKeys": ["engineDecision"], "suggestTools": ["compare_engine"]}
  ],
  "llm": {
    "provider": "deepseek",
    "model": "deepseek-chat",
    "apiKeyEnv": "DEEPSEEK_API_KEY",
    "systemPrompt": "你是资深支付风控专家。先覆盖已知风险，再基于数据组合推导未知风险假设。",
    "temperature": 0.2
  },
  "tools": [
    {"id": "read_context", "enabled": true},
    {"id": "list_check", "enabled": true},
    {"id": "analyze_amount_spike", "enabled": true, "spikeRatio": 3, "spikeAbsolute": 100000},
    {"id": "check_known_risks", "enabled": true},
    {"id": "read_indicator", "enabled": true, "refs": [{"refName": "b2b_daily_amt", "windowDays": 1, "granularity": "DAY"}]},
    {"id": "compare_engine", "enabled": true}
  ],
  "rules": [
    {"when": "blackHit", "decision": "REJECT", "reason": "已知风险：黑名单", "confidence": 0.95},
    {"when": "amount_spike", "decision": "REVIEW", "reason": "已知风险：突发巨额", "confidence": 0.9}
  ],
  "defaultDecision": "PASS",
  "defaultConfidence": 0.75,
  "defaultReason": "已知风险未命中且无显著异常"
}'
WHERE code = 'default_agent';
