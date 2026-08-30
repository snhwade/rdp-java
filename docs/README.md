# 风控实时决策平台 · 文档索引

| 文档 | 说明 |
|------|------|
| [requirements.md](./requirements.md) | 需求规格（17 条需求） |
| [design.md](./design.md) | 总体设计（架构、DDD 拆分、数据模型） |
| **[architecture-indicator-pipeline.md](./architecture-indicator-pipeline.md)** | **指标累计链路架构（Flink → Kafka → 多路存储）** |
| [enhancement-plan.md](./enhancement-plan.md) | 增强规划 |
| [param-mgmt-lineage.md](./param-mgmt-lineage.md) | 参数管理与血缘 |
| [decision-flow-optimization.md](./decision-flow-optimization.md) | 决策流优化 |
| [indicator-optimization.md](./indicator-optimization.md) | 指标优化 |
| [rule-mgmt-optimization.md](./rule-mgmt-optimization.md) | 规则管理优化 |
| [rating-model-optimization.md](./rating-model-optimization.md) | 评级模型优化 |
| [list-mgmt-optimization.md](./list-mgmt-optimization.md) | 名单管理优化 |
| [ai-decision-optimization.md](./ai-decision-optimization.md) | AI 决策优化 |
| [query-observability-optimization.md](./query-observability-optimization.md) | 查询与可观测性 |
| [ops-governance-optimization.md](./ops-governance-optimization.md) | 运维与治理 |

## 新人阅读顺序

1. [design.md](./design.md) — 了解平台三条链路与微服务划分  
2. [architecture-indicator-pipeline.md](./architecture-indicator-pipeline.md) — 理解旁路指标如何累计与读取  
3. [requirements.md](./requirements.md) — 查阅具体验收条款  
