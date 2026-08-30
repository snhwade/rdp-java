# 查询与监控优化定稿

> 更新：2026-08-24  
> 状态：XT1 / XL1 / XS1 **已实现**；XM0 / XE0 / XA0 本期不做  
> 说明：用户明确拒绝 XA1「简单超阈提示」——告警仍交 Grafana/值班，控制台不做提示类告警产品。

---

## 产品边界

- 平台提供：按调用/订单查询、执行链路排障、时段内**通用统计数字**。
- **不负责**业务是否改规则、如何处置；不做「建议调哪条规则」类结论。
- **不做**：控制台值班告警平台、完整 APM 替代 Grafana、业务分析报告产品。

---

## 已确认方案

| ID | 主题 | 结论 |
|----|------|------|
| **XT1** | 执行链路深度 | 扩展链路：选择器匹配 + 规则执行序列（含未命中摘要）+ 决策流节点路径（若有） |
| **XL1** | 页面打通 | 调用详情 / 订单详情 → 一键打开「执行链路」并带上 eventId；链路页可回跳调用详情 |
| **XS1** | 查询页通用统计 | 调用查询增加时段内决策分布、样本量、平均/P99 耗时（**无**建议文案）；AI 分歧统计保留 |

---

## 明确不做（本期）

| ID | 说明 |
|----|------|
| **XM0** | 监控四格指标做实 / 改外链 Grafana — 未选，不动 |
| **XE0** | 调用/订单列表导出 — 未选，不动 |
| **XA0** | 控制台超阈简单提示 — **明确不要**；告警仍非控制台职责 |

---

## 实现清单

- [x] **XT1**：`EmbeddedEngineGateway` 写入 `records`/`selectorMatch`/`flowPath`/`flowTrace`；网关 `InvocationTraceView` + `GET /decision-records/{eventId}/trace`；`DecisionTraceView` 三阶段展示
- [x] **XL1**：调用详情 / 订单列表 → `/observability?eventId=`；链路页「返回调用详情」
- [x] **XS1**：`GET /engine-decision-records/stats`（样本量、决策分布、avg/P99 耗时）；调用查询页与 AI 统计并存
- [x] BFF `/trace/{eventId}` 改走网关（与调用查询同源 `engine_decision_record`）

---

## 与其它模块关系

- 智能决策 IS：采纳模式/失败率等可复用调用侧数据，避免两套口径；本模块 XS1 偏引擎决策分布与耗时
- 规则试运行 S1：干跑通用统计；本模块管线上真实调用查询统计，不替代试运行报告
- Grafana / Prometheus：仍为运维主监控面；本页侧重「按事件排障 + 运营查数」

---

## 运维

- 重启 **decision-gateway 8081**、**admin-bff 8080**
- 重启 **rule-decision-engine 8083**（`RulePackageResult` 扩展，standalone 内嵌网关）
- 刷新 admin-console  
- 注：历史记录的 `detail_json` 无 `records` 时，链路页仍可展示命中规则（兼容模式）；新调用写入完整序列
