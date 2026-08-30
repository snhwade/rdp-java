# 系统增强计划（Enhancement Plan）

> 状态：执行中  
> 创建日期：2026-08-22  
> 范围：`risk-decision-platform` + `ai-training-service`  
> 原则：先产品正确性，再 AI 质量，再运维可靠，最后平台完整度；**现阶段不上多 Agent**。

---

## 1. 背景与目标

当前引擎事中链路可用；AI Agent 已能异步跑通，但默认 **SHADOW**，不参与对外决策；决策流 MODEL 节点缺少在线评分；观测与配置热更新尚未闭环。

目标：把 AI 从「可看的影子」升级为「可配置采纳的决策轨」，并补齐评分、名单实调用、分歧运营与运维基线。

---

## 2. 任务总览

| ID | 优先级 | 主题 | 状态 | 主要模块 |
| --- | --- | --- | --- | --- |
| **T1** | P0 | AI 采纳模式落地（SHADOW / ADVISORY / STRICT / OVERRIDE） | 已完成 | decision-gateway |
| **T2** | P0 | 在线评分 `POST /api/v1/ai/score` | 已完成 | ai-training-service, rule-decision-engine |
| **T3** | P0 | 引擎 vs AI 分歧运营化（统计 + 控制台） | 已完成 | gateway, BFF, admin-console |
| **T4** | P1 | 决策流名单检查走筛查/名单实调用 | 已完成 | rule-decision-engine |
| **T5** | P1 | AI 降级与启发式护栏 | 已完成 | decision-gateway |
| **T6** | P1 | 配置变更热更新（引擎消费 Kafka） | 已完成 | rule-config, rule-decision-engine |
| **T7** | P2 | 可观测补齐（metrics API + 控制台） | 已完成 | BFF, console, gateway |
| **T8** | P2 | Flink/指标链路对齐 + 一键启动含 8000 + BFF 错误文案修正 | 已完成（Flink 专项延后说明） | flink, scripts, admin-bff |

完成定义：每项任务需满足本节「验收标准」，并更新本表状态为 `已完成`。

---

## 3. 任务详述

### T1 — AI 采纳模式落地

**问题**  
`default-adoption-mode: SHADOW` 仅暴露在 runtime 接口；`RiskEventService` 始终以引擎（+名单）结果作为同步对外决策，AI 只异步落库。

**方案**

| 模式 | 同步行为 | 对外最终决策 |
| --- | --- | --- |
| `SHADOW`（默认） | 异步 AI，不阻塞 | `engine + 名单/筛查`（现状） |
| `ADVISORY` | 同步等待 AI（带超时） | 仍以引擎轨为准；AI 结论写入响应 `detail.ai`；若 AI 更严，**最多抬升到 REVIEW**（AI 不能单独 REJECT） |
| `STRICT` | 同步等待 AI（带超时） | `strictest(引擎轨, AI)`；超时/失败则回退引擎轨并标记 `aiTimedOut/aiFailed` |
| `OVERRIDE` | 同步等待 AI（带超时） | AI 成功则用 AI；失败/超时回退引擎轨 |

配置：

- `agent.orchestration.default-adoption-mode`
- `agent.orchestration.ai-sync-timeout-ms`（建议默认 `8000`）
- 策略 JSON 可选覆盖：`adoptionMode`

**改动点**

1. 新增 `AdoptionMode` 解析与合并器 `AiDecisionMerger`
2. `DecisionExecutionLogService` 支持同步 `adviseNow` + 仍写 `ai_decision_record`
3. `RiskEventService.accept` 按模式分支；SHADOW 保持异步
4. `RiskEventResult.detail` 增加 `adoptionMode`、`engineDecision`、`ai`（若有）
5. 单元测试覆盖四种模式

**验收**

- [x] SHADOW：响应不等待 LLM；`decision` 与引擎轨一致（名单合并后）
- [x] ADVISORY：响应含 AI；AI=REJECT 时对外最多 REVIEW
- [x] STRICT：引擎 PASS + AI REJECT → 对外 REJECT
- [x] OVERRIDE：优先 AI；AI 失败回退引擎
- [x] 超时不拖垮主链路（回退 + 记录）

---

### T2 — 在线评分 API

**问题**  
引擎 `AiScoreClient` 已约定 `POST /api/v1/ai/score`，Python 服务尚未实现，MODEL 节点持续降级。

**方案**

1. `ai-training-service` 实现 `POST /api/v1/ai/score`  
   请求：`{ "modelRef": "fraud|anomaly|...", "features": { ... } }`  
   响应：`{ "score": number, "label": string|null, "modelVersion": string|null, "available": true }`
2. 从最近成功训练任务或本地模型注册表加载模型；无模型时 `available=false` + 明确原因（HTTP 200 或约定不可用体，与 `ModelScoreResult.unavailable` 对齐）
3. 引擎侧无需改路径；补集成测试/契约测试
4. 文档注明 MODEL 节点 `modelRef` 与训练产出映射

**验收**

- [x] curl 打分返回数值 score（有训练模型时）/ available=false（无模型时）
- [x] 引擎客户端识别 `available=false`
- [x] 无模型时降级可解释，不抛崩引擎

---

### T3 — 分歧运营化

**问题**  
已有 `divergence` 字段与 AI/引擎记录页，缺汇总与运营动作。

**方案**

1. 网关：`GET /api/v1/ai-decision-records/stats?start&end` → 总量、SUCCESS/FAILED、divergence 率、按 eventType 分布
2. BFF 透传；控制台「AI 决策记录」增加统计条 + 一键筛选「仅分歧」
3. （可选）导出 CSV / 标注接口留扩展位

**验收**

- [x] 管理端可见分歧率与筛选（调用查询页统计条 +「仅分歧」）
- [x] 与库内 `divergence=true` 一致（AI 表筛选）

---

### T4 — 决策流名单实调用

**问题**  
部分 LIST_CHECK 依赖上下文预注入 `blackHit`，未稳定调用 screening/list 服务。

**方案**

1. `ListCheckNodeHandler` 优先调 `ListGateway` / `ScreeningGateway`（或引擎内既有 client）
2. 将命中结果写入上下文与 trace
3. 保留 context 注入作为兼容回退，打 warn 日志

**验收**

- [ ] 无预注入 flag 时，节点仍能命中黑名单样例
- [ ] dry-run / 单测覆盖

---

### T5 — AI 降级护栏

**问题**  
LLM 失败可能静默落到启发式 / 默认 PASS，生产难以察觉。

**方案**

1. 配置 `agent.orchestration.allow-heuristic-fallback`（默认本地 true、建议 remote false）
2. 失败原因写入 `ai_decision_record.failReason` / trace
3. 指标：`ai_advise_success_total` / `ai_advise_fail_total` / `ai_llm_unavailable_total`

**验收**

- [ ] 关闭启发式时，LLM 失败 → FAILED 或明确降级标记，不静默 PASS
- [ ] Micrometer 计数可刮取（或至少日志结构化）

---

### T6 — 配置热更新

**问题**  
rule-config 有 Kafka 发布，引擎缺少稳定的缓存失效消费。

**方案**

1. 引擎监听配置变更 topic，按类型失效规则包/决策流/指标缓存
2. 管理端「强制刷新」可选 API
3. 启动时全量加载 + 变更增量

**验收**

- [ ] 改规则包后无需重启引擎即可在下一次评估生效（或 ≤ 配置的 TTL）
- [ ] 消费失败有告警日志

---

### T7 — 可观测补齐

**问题**  
控制台 observability 页缺少完整 BFF metrics；缺统一 tracing。

**方案**

1. BFF `GET /bff/api/v1/observability/metrics` 聚合下游 actuator/自定义指标
2. 控制台对接真实数据
3. 日志/MDC 贯穿 `eventId`、`correlationId`（OTel 可作为后续迭代）

**验收**

- [ ] 控制台不再 TODO 空数据
- [ ] 能按 eventId 关联引擎记录与 AI 记录

---

### T8 — 指标链路与本地启动体验

**问题**  
Flink 与设计双写不完全一致；启动脚本未含 `ai-training-service:8000`；BFF 下游失败文案误报 8082。

**方案**

1. 修正 `WebClientDownstreamClient` 错误信息带上实际 baseUrl/服务名
2. `start-remote-all.ps1` 启动 Python 8000（JDK17 + MYSQL_URL）
3. Flink：补 ES sink 或文档声明「生产以 indicator-store SERVICE 双写为准」并缩小 Flink 职责

**验收**

- [ ] 8000 未启动时提示含 ai-training/8000
- [ ] 一键脚本可拉起训练服务
- [ ] Flink 职责与文档一致

---

## 4. 明确不做（本阶段）

- 多 Agent / Supervisor 架构
- 用 AI 完全替换规则引擎
- 大改前端视觉体系
- 无验收标准的「重构一切」

---

## 5. 执行顺序

```text
T1 → T2 → T3 → T5 → T4 → T6 → T7 → T8
```

说明：T5 紧跟 T1/T2，避免采纳模式上线后静默启发式污染生产；T4 与引擎正确性相关可并行，但排在采纳与评分之后。

---

## 6. 进度记录

| 日期 | 任务 | 说明 |
| --- | --- | --- |
| 2026-08-22 | 文档 | 创建本计划 |
| 2026-08-22 | T1 | 采纳模式合并器 + 同步 adviseSync + RiskEventService 分支；单测通过 |
| 2026-08-22 | T2 | 实现 POST /api/v1/ai/score；ModelStore 打分；AiScoreClient 识别 available |
| 2026-08-22 | T3 | AI stats API + 调用查询页分歧率/仅分歧筛选 |
| 2026-08-22 | T5 | allow-heuristic-fallback + AiLlmUnavailableException + Micrometer 计数 |
| 2026-08-22 | T4 | ListCheckPort/RestListCheckClient；节点优先实调用，回退上下文 |
| 2026-08-22 | T6 | ConfigCacheRegistry + Kafka listener（可开关）+ /api/v1/config-cache |
| 2026-08-22 | T7 | BFF GET /observability/metrics；控制台去掉 TODO |
| 2026-08-22 | T8 | BFF 下游错误带实际服务名；start-remote-all 启动 8000；Flink 职责写入文档 |

---

## 文档说明（T8 / Flink）

指标累计生产路径以 **indicator-store SERVICE 模式双写 Redis+ES** 为主；Flink 作业当前以 Redis sink 为主，作为旁路累计备选。完整 ES sink + DLQ 对齐可作为后续专项，不阻塞本增强计划其余项。

---

## 7. 关联文档

- `docs/design.md` — 总体设计
- `docs/requirements.md` — 需求
- Agent 运行时：`GET /api/v1/agent/runtime`
- AI 记录：`GET /api/v1/ai-decision-records`
