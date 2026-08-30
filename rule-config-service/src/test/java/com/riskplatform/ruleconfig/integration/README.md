# 集成测试基础设施约定（Feature: risk-console-redesign）

本目录提供本期 risk-console-redesign 四大模块集成测试的公共基础设施。集成测试为
**硬性必须执行项（Requirement 15）**，使用真实服务进程 + 真实 MySQL/Redis 验证，
**连接缺失视为失败而非跳过**。

## 连接方式（环境变量）

集成测试经环境变量连接真实依赖；未设置时使用与 `application.yml` 对齐的默认值。
可复用 `deploy/docker-compose.yml`（ES/Kafka/Flink），MySQL 与 Redis 使用本机已运行实例。

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `MYSQL_HOST` | `localhost` | MySQL 主机 |
| `MYSQL_PORT` | `3306` | MySQL 端口 |
| `MYSQL_DB` | `risk_decision_platform` | 库名 |
| `MYSQL_USER` | `root` | 用户名 |
| `MYSQL_PASSWORD` | `root` | 密码 |
| `REDIS_HOST` | `localhost` | Redis 主机 |
| `REDIS_PORT` | `6379` | Redis 端口 |
| `REDIS_PASSWORD` | （空） | Redis 密码（可选） |

## 基类

- `IntegrationTestEnvironment`：解析上述环境变量，并提供 `requireMysqlAvailable()` /
  `requireRedisAvailable()`。连通性校验不通过时**抛 `IllegalStateException` 使测试失败，
  绝不调用任何跳过逻辑**（R15.2/R15.3）。
- `AbstractMySqlIntegrationTest`：仅需 MySQL 的集成测试基类，注入真实数据源并运行真实
  Spring 上下文与 Flyway 迁移；启动前校验 MySQL 可用。
- `AbstractMySqlRedisIntegrationTest`：同时需要 MySQL 与 Redis 的集成测试基类；启动前校验两者均可用。

## 迁移与种子约定（本期）

- 本期 Flyway 版本化迁移从 **V19** 起（既有迁移已到 V18），位于 `src/main/resources/db/migration`，
  幂等且保留既有数据（R14.3）。
- 种子数据使用可重复迁移 **`R__seed_*.sql`**，以幂等 upsert
  （`INSERT ... ON DUPLICATE KEY UPDATE` 或唯一键冲突即跳过）实现，重复执行不产生重复记录（R15.6）。
- 迁移与种子脚本命名一律中性，禁止出现厂商专有名词（R1.3）。

## 属性测试（PBT）

- 复用 `net.jqwik`（版本由父 POM 统一为 `jqwik.version`）。每个属性 ≥100 次迭代，
  注释格式：`Feature: risk-console-redesign, Property {n}: ...`。
