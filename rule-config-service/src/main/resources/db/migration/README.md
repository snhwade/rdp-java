# 数据库迁移与种子约定（risk-console-redesign）

本目录由 Flyway 管理（`spring.flyway.locations=classpath:db/migration`，`baseline-on-migrate=true`）。
本期 risk-console-redesign 在现有 `risk-decision-platform` 之上做**增量改造**，迁移与种子须遵循以下约定。

## 1. 版本化迁移（Versioned Migrations）

- 既有迁移已到 **V18**（`V18__decision_flow_ext.sql`）。
- **本期新增版本化迁移一律从 `V19` 起**，按规划顺序递增：
  - `V19` 事件扩展（`event_type` 新增 `scenario_id`/`event_kind`/`purposes_json`）
  - `V20` 事件—字段关联表 `event_field`
  - `V21` 验证策略扩展（`strategy_def` 新增 `priority`/`scope_scenario_id`/`any_scope`）
  - `V22` 规则三态（`rule_v2.status` 扩展为 `ONLINE/TRIAL_RUN/OFFLINE` 并映射既有数据）
  - `V23` 评级模型相关表（`rating_model`/`rating_grade_band`/`rating_item`/`rating_model_version`）
- 每个迁移必须**幂等**并**保留既有数据**（R14.3）：
  - 列新增优先使用 `ADD COLUMN IF NOT EXISTS`（MySQL 8）或在变更前以 `information_schema` 判定，确保重复执行不报错；
  - 数据映射（如 `ENABLED→ONLINE`）使用条件 `UPDATE`，重复执行结果稳定。

## 2. 可重复种子脚本（Repeatable Seed Migrations）

- 种子数据使用 Flyway **可重复迁移**，命名前缀 `R__seed_`，例如：
  - `R__seed_param_management.sql`（业务场景、事件、字段库、事件字段、验证策略）
  - `R__seed_rules.sql`（规则包及含上线+试运行规则）
  - `R__seed_flows.sql`（含上线版本决策流）
  - `R__seed_rating.sql`（评级模型：商户·实时/对私·定时、评分定级/直接定级）
- 种子脚本必须以**幂等 upsert** 实现，重复执行不产生重复记录（R15.6）：
  - 优先 `INSERT ... ON DUPLICATE KEY UPDATE`（依赖业务唯一键，如事件 code、字段 code、策略 code）；
  - 或先 `SELECT` 判定存在性再插入。
- 可重复迁移在其校验和（checksum）变化时由 Flyway 在版本化迁移之后重新执行，故种子脚本应能在
  最新 schema 上反复安全运行。

## 3. 命名中性化（R1.3）

- 迁移与种子脚本文件名、注释、对象名一律使用中性的"风控/反欺诈平台"命名，
  **禁止出现任何产品厂商专有名词**。
- 命名中性化构建期扫描器（见 `build-tools/NamingNeutralityScan.java`）会扫描本期新增的
  `V19+` 迁移与 `R__seed_*` 种子脚本，命中即构建失败。

## 4. 集成测试（R15.2/R15.3）

- 集成测试经环境变量连接**真实 MySQL/Redis**，连接缺失**视为失败而非跳过**。
- 基础设施见 `src/test/.../integration/`：
  - `IntegrationTestEnvironment`：解析 `MYSQL_URL`（或 `MYSQL_HOST/PORT/DB`）/`MYSQL_USER`/`MYSQL_PASSWORD`、
    `REDIS_HOST`/`REDIS_PORT`/`REDIS_PASSWORD`，默认对齐本机实例（root/root @ localhost:3306、localhost:6379）；
  - `AbstractMySqlIntegrationTest`：仅需 MySQL 的集成测试基类；
  - `AbstractMySqlRedisIntegrationTest`：同时需要 Redis 的集成测试基类。
