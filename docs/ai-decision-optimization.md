# 智能决策优化定稿

> 更新：2026-08-24  
> 状态：IA1 / IM2 / IS **已实现**

---

## 产品边界

- 平台提供：Agent 策略配置、采纳模式、训练/模型版本、在线评分、运行数据统计。
- **不负责**业务是否放量、用哪档采纳模式、启用哪个模型的业务结论。
- **不做**多 Agent、自动「建议启用模型」、分歧自动训练上线。

---

## 已确认方案

| ID | 主题 | 结论 |
|----|------|------|
| **IA1** | 采纳模式运营 | 策略页**突出展示/编辑 adoptionMode**；记录变更留痕（谁、何时改） |
| **IM2** | 模型启用门禁 | 可选：「训练成功**不**自动设为 current」；启用前展示 AUC 等**通用指标**，**无建议文案**；需在模型管理手动启用 |
| **IS** | 运行通用统计 | 智能决策相关页或观测补齐：采纳模式分布、AI 失败/超时、评分 `available` 率等（无上线建议） |

---

## 未选 / 本期不做

| ID | 说明 |
|----|------|
| **IC** | Agent vs MODEL 分工说明页 — 维持现有顶部说明 |
| **ID0** | 分歧标注回流 — 不做 |
| **IF0** | Agent 字段血缘纳入 Q1-C — 仍挂参数管理待办，本期智能决策不单独做 |
| 多 Agent / 建议启用 | 明确不做 |

---

## 实现说明

| ID | 落地 |
|----|------|
| **IA1** | Flyway **V46**：`agent_strategy.adoption_mode` + `agent_strategy_adoption_audit`；策略页列表/表单 + 变更留痕 Drawer；网关按策略 `adoptionMode` 解析（回落 yml 默认） |
| **IM2** | `AUTO_PROMOTE_ON_SAVE=false`（默认）；`save_version(..., promote_to_current=)`；模型管理启用 Popconfirm 展示 AUC/KS |
| **IS** | 扩展 `GET /ai-decision-records/stats`：failRate、timedOut、byAdoptionMode、modelScoreAvailableRate；训练中心顶部统计卡片 |

---

## 与已有能力关系

- 模型管理、备注、训练 Tab、分歧筛选、采纳模式后端：已具备，本期是**运营化补强**而非从零建设。
- 调用查询分歧统计保留；IS 复用/聚合其数据，避免两套口径。
