# risk-decision-commons

**风控实时决策平台 · 公共 Java 库**

本仓库是 [Risk Decision Platform](https://github.com/snhwade/risk-decision-services) 的共享基础库，供所有 Java 微服务依赖。将通用能力从业务服务中抽离，避免重复实现，并保证错误码、鉴权、加解密等行为在全平台一致。

## 项目定位

| 项 | 说明 |
|---|---|
| 模块 | `commons-core` |
| 语言 | Java 17 |
| 构建 | Maven |
| 发布方式 | `mvn install` 安装到本地/私服，**不单独部署** |

## 核心能力

### 共享领域模型（`com.riskplatform.common.model`）

- `PagedResult` — 统一分页结构
- `IndicatorSliceUpdate` — Flink 下发的指标切片增量 Kafka 事件（与 data-engine / indicator-store 共用契约）

### 统一错误处理（`com.riskplatform.common.error`）

- `ErrorCode` / `CommonErrorCode` — 平台统一错误码体系
- `BizException` / `ValidationException` — 业务与校验异常
- `ErrorResponse` — REST 结构化错误响应体

### 全局 Web 层（`com.riskplatform.common.web`）

- `GlobalExceptionHandler` — 捕获异常并输出统一 JSON 错误格式

### 安全与鉴权（`com.riskplatform.common.security`）

- JWT 校验：`JwtTokenVerifier`、`JwtAuthenticationFilter`
- 401/403 处理：`RestAuthEntryPoint`、`RestAccessDeniedHandler`
- 基于 JJWT（HS256），与 `rule-config-service` 签发的 Token 配合使用

### 字段加解密（`com.riskplatform.common.crypto`）

- AES-256-GCM 字段级加密
- MyBatis `EncryptedStringTypeHandler` — 敏感字段透明加解密落库
- 用于名单条目、网关订单等敏感数据

### 通用工具（`com.riskplatform.common.model` / `util`）

- `PagedResult` — 统一分页响应
- `RetryExecutor` — 带退避的重试封装

## 目录结构

```
commons-core/
└── src/main/java/com/riskplatform/common/
    ├── crypto/      # 字段加解密
    ├── error/       # 错误码与异常
    ├── model/       # 通用模型
    ├── security/    # JWT 与安全
    ├── util/        # 工具类
    └── web/         # 全局异常处理
```

## 构建

**前置**：JDK 17+、Maven 3.8+

```powershell
mvn clean install -DskipTests
```

安装后，其他 Java 服务通过 Maven 依赖引用：

```xml
<dependency>
    <groupId>com.riskplatform</groupId>
    <artifactId>commons-core</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

## 使用说明

1. **先构建本仓库**，再构建 [risk-decision-services](https://github.com/snhwade/risk-decision-services)
2. 不要在业务模块中复制粘贴错误码或 JWT 逻辑，应扩展 `commons-core`
3. 修改公共 API 时需同步升级所有依赖服务的版本

## 关联仓库

| 仓库 | 说明 |
|------|------|
| [risk-decision-services](https://github.com/snhwade/risk-decision-services) | Java 微服务后端（本库的主要消费者） |
| [risk-decision-data-engine](https://github.com/snhwade/risk-decision-data-engine) | 数据引擎（旁路计算，含 Flink 指标累计） |
| [risk-decision-admin-console](https://github.com/snhwade/risk-decision-admin-console) | 管理控制台前端 |
| [risk-decision-ai-training](https://github.com/snhwade/risk-decision-ai-training) | AI 训练与在线评分 |

## 许可证

开源依赖均来自 Maven Central；本项目遵循平台整体开源策略。
