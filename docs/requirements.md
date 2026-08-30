# Requirements Document

## Introduction

本文档定义"风控决策平台（Risk Decision Platform）"的需求。该平台是一套全新、独立的实时风控决策系统，基于开源技术栈构建。平台围绕"事件 → 规则匹配 → 规则引擎执行 → 决策引擎按优先级聚合决策"的核心链路展开，支持事中（实时）风控处置，并通过流式计算累计风控指标供规则引用。

平台覆盖的典型业务场景包括：B2B/B2C 收款、付款、退款等交易事件的事中风控；名单/制裁/道琼斯类筛查；商户风险评级；以及基于历史交易数据的 AI 模型训练与指标提取增强能力。

技术与架构约束（由业务方明确指定）：

- 事中处理的订单数据落库于 MySQL。
- 风控指标数据同时落库到两套存储方案：Redis（切片形式）与 Elasticsearch（ES）。
- 指标累计采用 Flink 流式计算；业务方在订单处理完成后将终态数据推送到 Kafka，Flink 从 Kafka 消费数据并依据累计脚本计算指标。
- 后端采用领域驱动设计（DDD），可拆分为多个服务；前端统一收敛到单一前端工程。
- 所有依赖必须为开源依赖，禁止使用任何私有仓库依赖（如 `com.xgd.crossborder.commons.*`）。

本文档使用 EARS 模式描述需求，确保每条验收标准结构化、可测试。

## Glossary

- **Risk_Decision_Platform（风控决策平台）**：本系统整体，包含下述各子系统/服务。
- **Event_Service（事件服务）**：负责接收、校验、登记风控事件，并触发后续风控链路的服务。
- **Risk_Event（风控事件）**：一次需要风控判定的业务动作，如收款、付款、退款的发起或入账，包含事件类型、业务主体与事件上下文。
- **Event_Type（事件类型）**：风控事件的分类标识（code 唯一），如 B2B 收款、B2C 收款、付款发起、退款发起等。
- **Rule（规则）**：针对某一事件类型定义的、可执行的风控判定逻辑，包含规则表达式、版本与状态。
- **Rule_Engine（规则引擎）**：解析并执行规则表达式、产出规则命中结果的子系统。
- **Rule_Group（规则组）**：一组规则的集合，用于在同一事件下组织规则的执行。
- **Rule_Selector（规则选择器）**：依据事件上下文中的键值选择应执行的规则组的组件。
- **Decision（决策）**：规则命中后产生的处置结论，取值包括放行（PASS）、人工复核（REVIEW）、拦截（REJECT）等。
- **Decision_Engine（决策引擎）**：依据决策优先级，对多条命中规则产生的决策进行聚合，产出最终单一决策的子系统。
- **Decision_Priority（决策优先级）**：决策的排序数值，数值越小优先级越高。
- **Indicator（指标）**：可被规则引用的统计度量，如某商户近 N 天交易笔数、交易金额合计、交易对手数量等。
- **Indicator_Definition（指标定义）**：描述指标含义、统计维度、时间窗口、累计脚本的配置。
- **Indicator_Accumulation（指标累计）**：基于订单终态数据对指标进行流式累加计算的过程。
- **Stream_Compute_Service（流计算服务）**：基于 Flink 实现的流式指标累计计算服务。
- **Kafka_Topic（Kafka 主题）**：业务方推送订单终态数据所使用的消息主题。
- **Order_Store（订单存储）**：基于 MySQL 的事中订单数据持久化存储。
- **Indicator_Store（指标存储）**：指标数据的持久化存储，包含 Redis 切片存储与 ES 存储两套实现。
- **Redis_Slice_Store（Redis 切片存储）**：以时间切片形式在 Redis 中保存指标数据的存储实现。
- **ES_Store（ES 存储）**：在 Elasticsearch 中保存指标数据的存储实现。
- **Screening_Service（筛查服务）**：执行名单/制裁/道琼斯类名称筛查的服务。
- **Merchant_Rating_Service（商户评级服务）**：基于评级因子与指标计算商户风险等级与评分的服务。
- **AI_Training_Service（AI 训练服务）**：基于落库的历史交易数据训练模型并提取交易对手关系指标的增强服务。
- **Admin_Console（管理前端）**：统一的前端工程，供风控运营人员配置事件、规则、指标与查看决策结果。
- **Risk_Operator（风控运营人员）**：在管理前端进行配置与处置的用户角色。
- **Business_System（业务方系统）**：调用本平台进行风控判定、并推送订单终态数据的外部系统。
- **On_Process（事中处理）**：在业务交易进行过程中实时进行的风控判定。
- **事件类型管理页（Event_Type_Management_Page）**：Admin_Console 中用于查看、创建、编辑事件类型并启用/禁用的页面。
- **规则配置页（Rule_Config_Page）**：Admin_Console 中用于编辑规则表达式、查看规则版本、切换启用/禁用并选择关联事件类型的页面。
- **规则组与选择器配置页（Rule_Group_Selector_Page）**：Admin_Console 中用于创建/编辑规则组、向规则组关联规则、配置选择器优先级与匹配方式、并启用/禁用规则组的页面。
- **决策结果查看页（Decision_Result_Page）**：Admin_Console 中用于按事件查看最终决策与参与聚合的命中规则及其各自决策的页面。
- **决策优先级配置页（Decision_Priority_Config_Page）**：Admin_Console 中用于配置规则决策优先级与决策超时处置策略的页面。
- **指标定义配置页（Indicator_Definition_Page）**：Admin_Console 中用于配置指标定义（引用名、统计维度、时间窗口、切片粒度、累计脚本）并查看引用关系的页面。
- **订单查询页（Order_Query_Page）**：Admin_Console 中用于按商户、事件类型与时间范围筛选并分页查看订单及其最终决策的页面。
- **名单与筛查配置页（Screening_Config_Page）**：Admin_Console 中用于配置名称匹配相似度阈值并查看筛查命中结果的页面。
- **商户评级查看页（Merchant_Rating_Page）**：Admin_Console 中用于触发商户风险评级计算并查看风险评分与风险等级的页面。
- **AI 训练任务页（AI_Training_Page）**：Admin_Console 中用于选择数据时间范围触发模型训练并查看训练状态、数据范围、模型版本与评估指标的页面。
- **执行链路查询与监控页（Observability_Page）**：Admin_Console 中用于按事件标识查询完整执行链路并查看监控指标的页面。

## Requirements

### Requirement 1: 事件类型管理

**User Story:** 作为风控运营人员，我想要管理多种风控事件类型，以便平台能够针对不同业务场景灵活地接入与配置风控逻辑。

#### Acceptance Criteria

1. WHEN 风控运营人员提交一个包含名称（长度为 1 至 100 个字符）与唯一 code（长度为 1 至 64 个字符，且仅由字母、数字与下划线组成）的事件类型创建请求，THE Event_Service SHALL 持久化该事件类型、将其初始状态设置为启用并返回其唯一标识。
2. IF 提交的事件类型 code 与已存在的事件类型 code 重复，THEN THE Event_Service SHALL 拒绝该请求、不持久化该事件类型并返回指示 code 重复的错误信息。
3. IF 提交的事件类型创建请求缺少名称或 code、名称长度超过 100 个字符、code 长度超过 64 个字符、或 code 包含字母、数字、下划线以外的字符，THEN THE Event_Service SHALL 拒绝该请求、不持久化该事件类型并返回指示输入校验失败的错误信息。
4. WHEN 风控运营人员将某事件类型状态设置为禁用，THE Event_Service SHALL 将该事件类型状态更新为禁用。
5. WHILE 某事件类型处于禁用状态，THE Event_Service SHALL 拒绝处理该事件类型对应的风控事件并返回指示事件类型已禁用的错误信息。
6. WHEN 风控运营人员查询事件类型列表，THE Event_Service SHALL 返回当前所有事件类型及其名称、code 与状态；当不存在任何事件类型时，THE Event_Service SHALL 返回空列表。
7. WHEN 风控运营人员打开 Admin_Console 的事件类型管理页，THE Admin_Console SHALL 以列表形式展示全部事件类型的名称、code 与启用/禁用状态，并提供创建、编辑与启用/禁用操作入口。
8. WHEN 风控运营人员在 Admin_Console 的事件类型管理页提交事件类型创建或编辑表单，THE Admin_Console SHALL 将名称、code 与状态提交至 Event_Service，并在提交成功后向风控运营人员展示更新后的事件类型列表。
9. IF Event_Service 因 code 重复或输入校验失败而拒绝事件类型创建或编辑请求，THEN THE Admin_Console SHALL 在事件类型管理页对应表单项上展示该错误原因且保留风控运营人员已填写的内容。

### Requirement 2: 风控事件接收与受理

**User Story:** 作为业务方系统，我想要向平台提交风控事件，以便在交易进行过程中获得实时风控判定结果。

#### Acceptance Criteria

1. WHEN 业务方系统提交一个携带事件类型 code 与事件上下文的风控事件请求，且该事件类型存在并处于启用状态，THE Event_Service SHALL 受理该事件，并在 1 秒（1000 毫秒）内向业务方系统返回包含唯一事件标识的受理成功响应。
2. IF 提交的风控事件请求缺少事件类型 code 或缺少事件上下文，THEN THE Event_Service SHALL 拒绝该请求、不生成事件标识且不触发规则匹配流程，并向业务方系统返回指示缺少必填字段的错误信息。
3. IF 提交的风控事件请求引用了不存在的事件类型 code，THEN THE Event_Service SHALL 拒绝该请求、不生成事件标识且不触发规则匹配流程，并向业务方系统返回指示事件类型不存在的错误信息。
4. IF 提交的风控事件请求引用的事件类型 code 存在但处于禁用状态，THEN THE Event_Service SHALL 拒绝该请求、不生成事件标识且不触发规则匹配流程，并向业务方系统返回指示事件类型已禁用的错误信息。
5. IF 提交的风控事件请求的事件类型 code 长度超过 64 个字符，或事件上下文序列化后大小超过 64 KB，THEN THE Event_Service SHALL 拒绝该请求、不生成事件标识且不触发规则匹配流程，并向业务方系统返回指示请求超出大小限制的错误信息。
6. WHEN 一个风控事件被成功受理，THE Event_Service SHALL 触发且仅触发一次该事件类型对应的规则匹配流程。
7. WHEN 一个风控事件被成功受理，THE Event_Service SHALL 为该事件记录全局唯一的事件标识、精确到毫秒的受理时间戳与事件上下文。

### Requirement 3: 规则配置与管理

**User Story:** 作为风控运营人员，我想要灵活配置规则，以便在不修改代码的情况下调整风控判定逻辑。

#### Acceptance Criteria

1. WHEN 风控运营人员提交一条包含规则表达式（长度为 1 至 4000 个字符）与非空关联事件类型 code 的规则创建请求，THE Rule_Engine SHALL 持久化该规则并将其版本号初始化为 1。
2. IF 提交的规则表达式语法无法被解析，THEN THE Rule_Engine SHALL 拒绝保存该规则并返回表达式语法错误的位置与描述。
3. WHEN 风控运营人员更新一条已存在的规则，THE Rule_Engine SHALL 保存新内容并将该规则版本号递增 1。
4. WHEN 风控运营人员将某规则状态设置为禁用，THE Rule_Engine SHALL 在 5 秒内使该规则在后续所有规则匹配中被排除。
5. THE Rule_Engine SHALL 仅允许规则表达式引用已在指标定义或事件上下文中声明的字段。
6. IF 规则表达式引用了未声明的字段，THEN THE Rule_Engine SHALL 拒绝保存该规则并返回未声明字段的名称。
7. IF 规则创建或更新请求中的规则表达式为空或超过 4000 个字符，或关联事件类型 code 为空，THEN THE Rule_Engine SHALL 拒绝保存该规则并返回未通过校验的字段及失败原因。
8. IF 风控运营人员更新的规则不存在，THEN THE Rule_Engine SHALL 拒绝该更新操作、保持已有规则数据不变，并返回规则不存在的错误提示。
9. WHEN 风控运营人员将某规则状态设置为启用，THE Rule_Engine SHALL 在 5 秒内使该规则纳入后续所有规则匹配。
10. WHEN 风控运营人员打开 Admin_Console 的规则配置页，THE Admin_Console SHALL 提供规则表达式编辑器、关联事件类型选择控件、规则版本号展示与启用/禁用切换控件。
11. WHEN 风控运营人员在 Admin_Console 的规则配置页保存规则，THE Admin_Console SHALL 将规则表达式与关联事件类型 code 提交至 Rule_Engine，并在保存成功后展示 Rule_Engine 返回的最新版本号。
12. IF Rule_Engine 因表达式语法错误或引用未声明字段而拒绝保存，THEN THE Admin_Console SHALL 在规则配置页展示语法错误的位置与描述或未声明字段的名称，并保留风控运营人员已编辑的规则表达式内容。

### Requirement 4: 规则组与规则选择器

**User Story:** 作为风控运营人员，我想要将规则组织为规则组并通过选择器按上下文选择，以便针对不同业务场景执行不同的规则集合。

#### Acceptance Criteria

1. WHEN 风控运营人员创建一个关联某事件类型 code 的规则组，且该事件类型存在并处于启用状态，THE Rule_Engine SHALL 持久化该规则组并允许向其中关联规则。
2. IF 创建规则组请求关联的事件类型 code 不存在或处于禁用状态，THEN THE Rule_Engine SHALL 拒绝该请求、不持久化该规则组，并返回指示事件类型不存在或已禁用的错误信息。
3. THE Rule_Selector SHALL 支持两种规则组匹配方式：基于事件上下文的选择键与选择值的简单键值匹配，以及基于选择器规则对事件上下文求值的规则匹配。
4. WHEN 一个风控事件触发规则匹配，THE Rule_Selector SHALL 在所有启用的选择器中按选择器优先级由高到低（优先级数值由小到大）依次评估，并选取第一个匹配成功的选择器所关联的规则组。
5. IF 多个匹配成功的选择器具有相同的最高优先级，THEN THE Rule_Selector SHALL 选取其中选择器唯一标识最小者，以保证选择结果的确定性。
6. WHERE 存在一个兜底通用选择器，WHEN 事件上下文未匹配到任何具备选择键或选择器规则的选择器，THE Rule_Selector SHALL 选取该兜底通用选择器所关联的规则组。
7. IF 事件上下文未匹配到任何选择器且不存在兜底通用选择器，THEN THE Rule_Selector SHALL 返回未匹配规则组的结果且不产生命中。
8. WHILE 某规则组处于禁用状态，THE Rule_Engine SHALL 在规则匹配中排除该规则组下的所有规则。
9. WHEN 风控运营人员打开 Admin_Console 的规则组与选择器配置页，THE Admin_Console SHALL 展示规则组列表及其关联事件类型、关联规则与启用/禁用状态，并提供规则组创建、编辑、向规则组关联规则与启用/禁用操作入口。
10. WHEN 风控运营人员在 Admin_Console 的规则组与选择器配置页配置某选择器，THE Admin_Console SHALL 允许风控运营人员设置该选择器的优先级、匹配方式（简单键值匹配或选择器规则匹配）与所关联的规则组，并将配置提交至 Rule_Engine。
11. IF Rule_Engine 因关联事件类型不存在或已禁用而拒绝规则组创建请求，THEN THE Admin_Console SHALL 在规则组与选择器配置页展示该错误原因且不清除风控运营人员已填写的内容。

### Requirement 5: 规则引擎执行

**User Story:** 作为业务方系统，我想要平台执行匹配到的规则，以便得到每条规则是否命中的判定结果。

#### Acceptance Criteria

1. WHEN 一个规则组被规则选择器选中，THE Rule_Engine SHALL 按规则优先级由高到低（优先级数值由小到大、数值相同时按规则标识升序）的确定顺序依次执行该规则组下所有启用状态的规则。
2. WHEN 规则引擎执行一条规则，THE Rule_Engine SHALL 基于事件上下文与被引用指标的当前值对规则表达式求值并产出命中或未命中结果。
3. IF 一条规则在执行过程中发生求值异常，THEN THE Rule_Engine SHALL 将该规则标记为执行失败、记录失败原因、将该规则计为未命中且不向决策聚合贡献决策，并继续执行同组内的其余规则。
4. IF 规则执行失败后的恢复处理（标记失败或记录失败原因）本身发生异常，THEN THE Rule_Engine SHALL 将其视为致命错误、停止执行当前规则组内的全部剩余规则、保留已产出的规则命中结果、记录致命错误原因，并将该规则组的执行状态标记为执行中断。
5. THE Rule_Engine SHALL 为每次规则执行记录规则标识、规则版本、执行上下文与命中结果。
6. WHERE 一条规则的决策被配置为短路（SHORT_CIRCUITED），WHEN 该规则在执行中命中，THE Rule_Engine SHALL 停止执行同组内优先级更低（优先级数值更大）的规则。

### Requirement 6: 决策引擎与优先级聚合

**User Story:** 作为风控运营人员，我想要决策引擎按优先级聚合命中规则的决策，以便在事中检测时产出优先级最高的唯一决策。

#### Acceptance Criteria

1. THE Decision_Engine SHALL 为每条规则决策维护一个取值范围为 1 至 9999 的整数决策优先级，且约定数值越小优先级越高。
2. WHEN 规则引擎对一个风控事件产出一条或多条命中规则的决策，THE Decision_Engine SHALL 选取其中决策优先级数值最小的决策作为该事件的最终决策。
3. IF 存在多条命中决策具有相同的最小决策优先级数值，THEN THE Decision_Engine SHALL 在受支持的取值（拦截 REJECT、人工复核 REVIEW、放行 PASS）范围内依据决策严格性次序选取处置最严格的决策作为最终决策，且默认严格性由严到宽为：拦截（REJECT）> 人工复核（REVIEW）> 放行（PASS）。
4. IF 一个风控事件没有任何规则命中，THEN THE Decision_Engine SHALL 产出默认放行（PASS）决策。
5. WHEN 决策引擎产出最终决策，THE Decision_Engine SHALL 在事件受理后的可配置时限内返回该决策，该时限取值范围为 1 至 5000 毫秒，默认时限为 500 毫秒。
6. WHEN 决策引擎产出最终决策，THE Decision_Engine SHALL 为该事件记录最终决策、参与聚合的全部命中规则及其各自决策。
7. IF 决策引擎在配置的决策时限内未能完成聚合，THEN THE Decision_Engine SHALL 依据预先配置的决策超时处置策略产出决策并记录超时原因。
8. WHEN 风控运营人员在 Admin_Console 的决策结果查看页按事件标识查看某事件，THE Admin_Console SHALL 展示该事件的最终决策、参与聚合的全部命中规则及其各自决策与决策优先级。
9. WHEN 风控运营人员打开 Admin_Console 的决策优先级配置页，THE Admin_Console SHALL 提供规则决策优先级（取值范围 1 至 9999）与决策超时处置策略的配置控件，并将配置提交至 Decision_Engine。
10. IF Decision_Engine 因决策优先级取值超出 1 至 9999 范围而拒绝配置，THEN THE Admin_Console SHALL 在决策优先级配置页展示取值范围错误并保留风控运营人员已填写的内容。

### Requirement 7: 指标定义管理

**User Story:** 作为风控运营人员，我想要定义可被规则引用的指标，以便规则能够基于累计统计量进行判定。

#### Acceptance Criteria

1. WHEN 风控运营人员提交一个包含唯一引用名（长度为 1 至 64 个字符、仅由字母、数字与下划线组成）、统计维度、时间窗口、切片粒度与累计脚本的指标定义，THE Stream_Compute_Service SHALL 持久化该指标定义并返回其唯一标识。
2. IF 指标定义缺少引用名、统计维度、时间窗口、切片粒度或累计脚本中的任一必填字段，THEN THE Stream_Compute_Service SHALL 拒绝该请求并返回缺少的必填字段名称。
3. IF 提交的指标引用名与已存在的指标引用名重复，THEN THE Stream_Compute_Service SHALL 拒绝该请求并返回指示引用名重复的错误信息。
4. IF 指标定义中的累计脚本语法无法被解析，THEN THE Stream_Compute_Service SHALL 拒绝保存该指标定义并返回累计脚本语法错误的位置与描述。
5. THE Stream_Compute_Service SHALL 允许指标定义指定时间窗口长度（取值范围为 1 至 365 天）与切片粒度（分钟、小时或天之一），且时间窗口长度须为切片粒度的整数倍。
6. WHEN 风控运营人员更新一个被任意启用规则引用的指标定义，THE Stream_Compute_Service SHALL 在更新生效前提示该指标正被引用并列出引用它的全部规则标识。
7. WHEN 风控运营人员打开 Admin_Console 的指标定义配置页，THE Admin_Console SHALL 提供引用名、统计维度、时间窗口、切片粒度与累计脚本的表单控件，并将指标定义提交至 Stream_Compute_Service。
8. IF Stream_Compute_Service 因累计脚本语法错误、引用名重复或必填字段缺失而拒绝保存，THEN THE Admin_Console SHALL 在指标定义配置页展示该错误原因（含累计脚本语法错误的位置与描述）并保留风控运营人员已填写的内容。
9. WHEN 风控运营人员在 Admin_Console 的指标定义配置页更新一个被启用规则引用的指标定义，THE Admin_Console SHALL 在更新生效前展示 Stream_Compute_Service 返回的引用该指标的全部规则标识列表，并要求风控运营人员确认后再提交。

### Requirement 8: 基于 Flink 与 Kafka 的指标累计

**User Story:** 作为风控运营人员，我想要平台在订单完成后自动累计指标，以便规则可以使用最新的累计指标进行判定。

#### Acceptance Criteria

1. WHEN 业务方系统在订单处理完成后向 Kafka 主题推送一条订单终态数据，THE Stream_Compute_Service SHALL 从 Kafka 主题消费该数据。
2. WHEN 流计算服务消费到一条字段完整且可反序列化的订单终态数据，THE Stream_Compute_Service SHALL 依据所有统计维度所需字段均存在于该数据中的指标定义的累计脚本更新对应指标值。
3. WHEN 流计算服务完成一条订单终态数据的指标累计，THE Stream_Compute_Service SHALL 在可配置时延内（取值范围为 1 至 60 秒，默认 5 秒）使更新后的指标值可被规则引擎读取。
4. IF 一条订单终态数据无法被反序列化或缺少累计所需字段，THEN THE Stream_Compute_Service SHALL 跳过该消息的指标累计、将该消息路由至死信主题并记录失败原因，且继续消费后续消息。
5. IF 累计脚本在执行过程中发生异常，THEN THE Stream_Compute_Service SHALL 跳过该消息的指标累计、记录失败原因并触发告警，且继续消费后续消息。
6. THE Stream_Compute_Service SHALL 对订单终态数据按订单唯一标识进行幂等处理，使同一订单被重复消费时指标累计结果保持不变。
7. THE Stream_Compute_Service SHALL 依据指标定义的时间窗口对超出窗口的历史切片数据进行老化处理，使超出窗口的历史切片不再参与该指标当前值的计算。

### Requirement 9: 指标双存储（Redis 切片与 ES）

**User Story:** 作为平台，我想要将指标数据同时写入 Redis 切片存储与 ES 存储，以便兼顾低延迟读取与可检索分析两种使用方式。

#### Acceptance Criteria

1. WHEN 流计算服务完成一次指标累计，THE Indicator_Store SHALL 将更新后的指标数据写入 Redis_Slice_Store 与 ES_Store 两套存储。
2. THE Redis_Slice_Store SHALL 以指标定义所指定的切片粒度按时间切片保存指标数据。
3. WHEN 规则引擎在事中处理过程中从 Redis_Slice_Store 读取一个命中的指标值，THE Indicator_Store SHALL 在 50 毫秒内返回该指标值。
4. IF 规则引擎读取的指标值在 Redis_Slice_Store 中不存在或 Redis_Slice_Store 不可用，THEN THE Indicator_Store SHALL 回退至 ES_Store 读取该指标值；当 ES_Store 也不可读取时，THE Indicator_Store SHALL 返回指标不可读取的结果。
5. IF 向 ES_Store 写入指标数据失败，THEN THE Indicator_Store SHALL 最多重试 3 次，并在仍失败后记录失败、触发告警且不回滚、不影响 Redis_Slice_Store 的写入结果。
6. IF 向 Redis_Slice_Store 写入指标数据失败，THEN THE Indicator_Store SHALL 最多重试 3 次，并在仍失败后记录失败并触发告警。
7. THE Indicator_Store SHALL 使 Redis_Slice_Store 与 ES_Store 中同一指标在同一时间切片上的累计值在写入完成后 60 秒内达到最终一致。

### Requirement 10: 事中订单落库（MySQL）

**User Story:** 作为风控运营人员，我想要事中处理的订单被持久化到 MySQL，以便后续审计、查询与模型训练使用。

#### Acceptance Criteria

1. WHEN 一个风控事件在事中处理中被受理，THE Order_Store SHALL 将该订单的业务数据与事件上下文持久化到 MySQL，且对同一事件标识至多保存一条订单记录，并不阻塞事中决策的返回。
2. WHEN 决策引擎对一个订单产出最终决策，THE Order_Store SHALL 将该订单关联的风控事件标识与最终决策持久化到 MySQL。
3. IF 订单落库写入失败，THEN THE Order_Store SHALL 最多重试 3 次，并在仍失败后记录失败原因、触发告警，且不阻塞、不改变已返回的事中决策。
4. WHEN 风控运营人员按商户、事件类型或时间范围查询订单，THE Order_Store SHALL 在 3 秒内分页返回符合条件的订单及其最终决策（每页最多 200 条）；当无符合条件的订单时返回空列表。
5. IF 查询请求未提供任一过滤条件，或所提供的时间范围起始时间晚于结束时间，THEN THE Order_Store SHALL 拒绝该查询并返回查询条件无效的错误信息。
6. WHEN 风控运营人员打开 Admin_Console 的订单查询页，THE Admin_Console SHALL 提供商户、事件类型与时间范围的筛选控件，并将查询条件提交至 Order_Store 后分页展示符合条件的订单及其最终决策。
7. WHEN Order_Store 对订单查询返回空列表，THE Admin_Console SHALL 在订单查询页展示无符合条件订单的空态提示。
8. IF Order_Store 因未提供任一过滤条件或时间范围起始时间晚于结束时间而拒绝查询，THEN THE Admin_Console SHALL 在订单查询页展示查询条件无效的提示且不发起分页请求。

### Requirement 11: 名单与制裁筛查

**User Story:** 作为风控运营人员，我想要在事中对交易主体进行名单、制裁与道琼斯类筛查，以便识别受制裁或高风险对象。

#### Acceptance Criteria

1. WHEN 一个风控事件在事中处理中被受理，THE Screening_Service SHALL 对该事件中的交易主体名称依次进行名单、制裁与道琼斯类名单筛查。
2. WHEN 筛查服务发现交易主体名称与某名单条目的匹配相似度大于或等于已配置的相似度阈值，THE Screening_Service SHALL 返回命中结果，包含命中的名单来源、匹配条目与匹配相似度。
3. IF 交易主体名称与全部名单条目的匹配相似度均小于已配置的相似度阈值，THEN THE Screening_Service SHALL 返回未命中结果。
4. THE Screening_Service SHALL 允许风控运营人员配置名称匹配的相似度阈值，其取值范围为 0.00 至 1.00，默认 0.85。
5. IF 筛查服务在可配置时限（取值范围为 1 至 5000 毫秒，默认 500 毫秒）内未返回结果，THEN THE Decision_Engine SHALL 依据预先配置的筛查超时处置策略产出决策并记录超时原因。
6. IF 筛查执行发生异常或名单数据不可用，THEN THE Screening_Service SHALL 返回筛查失败结果并记录失败原因，且由 Decision_Engine 依据预先配置的筛查失败处置策略产出决策。
7. WHEN 风控运营人员打开 Admin_Console 的名单与筛查配置页，THE Admin_Console SHALL 提供名称匹配相似度阈值（取值范围 0.00 至 1.00）的配置控件，并将阈值提交至 Screening_Service。
8. WHEN 风控运营人员在 Admin_Console 的名单与筛查配置页查看某事件的筛查命中结果，THE Admin_Console SHALL 展示命中的名单来源、匹配条目与匹配相似度。
9. IF Screening_Service 因相似度阈值取值超出 0.00 至 1.00 范围而拒绝配置，THEN THE Admin_Console SHALL 在名单与筛查配置页展示取值范围错误并保留风控运营人员已填写的内容。

### Requirement 12: 商户风险评级

**User Story:** 作为风控运营人员，我想要平台基于评级因子与指标计算商户风险等级，以便在规则中引用商户风险评级。

#### Acceptance Criteria

1. WHEN 风控运营人员触发对某商户的风险评级计算，THE Merchant_Rating_Service SHALL 依据评级因子与指标值计算该商户的风险评分，评分取值范围为 0 至 100，且相同输入产出相同评分。
2. THE Merchant_Rating_Service SHALL 依据风险评分将商户映射到互不重叠且覆盖 0 至 100 全范围的五个风险等级之一：低（0–20）、中低（21–40）、中（41–60）、中高（61–80）、高（81–100）。
3. WHEN 商户风险评级计算完成，THE Merchant_Rating_Service SHALL 持久化该商户最新的风险等级与评分，并使其可被规则按商户引用。
4. IF 评级所需的评级因子或指标值缺失或不可读取，THEN THE Merchant_Rating_Service SHALL 保留该商户已有的评级结果不变，并返回数据不完整的错误信息。
5. WHERE 某商户尚未完成任何风险评级，WHEN 规则引用该商户的风险评级，THE Merchant_Rating_Service SHALL 返回未评级状态。
6. WHEN 风控运营人员在 Admin_Console 的商户评级查看页对某商户触发风险评级计算，THE Admin_Console SHALL 将计算请求提交至 Merchant_Rating_Service，并在计算完成后展示该商户的风险评分与所属风险等级。
7. WHEN 风控运营人员在 Admin_Console 的商户评级查看页查看某商户的风险评级，THE Admin_Console SHALL 展示该商户的风险评分（0 至 100）与五档风险等级（低、中低、中、中高、高）之一。
8. WHERE 某商户尚未完成任何风险评级，WHEN 风控运营人员在 Admin_Console 的商户评级查看页查看该商户，THE Admin_Console SHALL 展示未评级状态。

### Requirement 13: AI 模型训练与交易对手指标提取（增强能力）

**User Story:** 作为风控运营人员，我想要平台基于落库的历史交易数据训练模型并提取交易对手关系指标，以便强化风控识别能力。

#### Acceptance Criteria

1. WHERE AI 训练能力被启用，WHEN 风控运营人员触发一次指定数据时间范围的模型训练，THE AI_Training_Service SHALL 基于 MySQL 中落库的该时间范围内的历史交易订单数据训练风控模型。
2. WHERE AI 训练能力被启用，WHEN 一次模型训练成功完成，THE AI_Training_Service SHALL 基于交易对手之间的交易关系提取交易对手关系指标并写入 Indicator_Store。
3. WHEN 一次模型训练成功完成，THE AI_Training_Service SHALL 记录训练所用数据时间范围、模型版本与评估指标。
4. WHERE AI 训练能力被禁用，THE Risk_Decision_Platform SHALL 在不依赖 AI_Training_Service 的情况下完成事件、规则、决策与指标的全部核心功能。
5. WHERE AI 提取的交易对手关系指标已写入 Indicator_Store，THE Rule_Engine SHALL 允许规则引用该类指标。
6. IF 触发训练时落库的可用训练样本量小于可配置的最小训练样本量（取值范围为 1 至 1,000,000 条，默认 1000 条），THEN THE AI_Training_Service SHALL 拒绝该次训练并返回训练样本不足的错误信息。
7. IF 模型训练发生异常或超出可配置的最长训练时长（取值范围为 60 至 86400 秒，默认 3600 秒），THEN THE AI_Training_Service SHALL 终止该次训练、记录失败原因并触发告警，且不写入任何交易对手关系指标。
8. IF 交易对手关系指标写入 Indicator_Store 失败，THEN THE AI_Training_Service SHALL 最多重试可配置次数（取值范围为 1 至 10 次，默认 3 次），并在仍失败后记录失败并触发告警，且不影响事件、规则、决策与指标累计等核心功能。
9. WHERE AI 训练能力被启用，WHEN 风控运营人员在 Admin_Console 的 AI 训练任务页选择数据时间范围并触发训练，THE Admin_Console SHALL 将该时间范围提交至 AI_Training_Service 以启动模型训练。
10. WHEN 风控运营人员查看 Admin_Console 的 AI 训练任务页，THE Admin_Console SHALL 展示各训练任务的训练状态、所用数据时间范围、模型版本与评估指标。
11. IF AI_Training_Service 因训练样本不足或训练失败而返回错误，THEN THE Admin_Console SHALL 在 AI 训练任务页展示训练样本不足或训练失败的原因。

### Requirement 14: 前后端架构与开源依赖约束

**User Story:** 作为平台架构负责人，我想要前端统一收敛、后端按 DDD 拆分服务且全部使用开源依赖，以便系统独立、可维护且无私有仓库耦合。

#### Acceptance Criteria

1. THE Admin_Console SHALL 作为单一前端工程承载事件、规则、指标配置与决策结果查看的全部前端页面。
2. THE Risk_Decision_Platform SHALL 采用领域驱动设计组织后端代码，并允许后端按限界上下文拆分为多个独立服务。
3. THE Risk_Decision_Platform SHALL 仅依赖开源依赖。
4. IF 构建过程中检测到任何私有仓库依赖（含 `com.xgd.crossborder.commons.*`），THEN THE Risk_Decision_Platform 的构建 SHALL 失败并报告该私有依赖的坐标。

### Requirement 15: 可观测性

**User Story:** 作为风控运营人员，我想要查看决策、规则命中与指标累计的执行日志与监控指标，以便排查问题与审计。

#### Acceptance Criteria

1. WHEN 决策引擎产出一个最终决策，THE Risk_Decision_Platform SHALL 记录包含事件标识、命中规则、最终决策与处理耗时的决策日志。
2. THE Risk_Decision_Platform SHALL 暴露事件处理量、决策耗时与规则命中率的监控指标。
3. WHEN 风控运营人员按事件标识查询执行链路，THE Risk_Decision_Platform SHALL 返回该事件的规则匹配、规则执行与决策聚合的完整记录。
4. WHEN 风控运营人员在 Admin_Console 的执行链路查询与监控页按事件标识查询，THE Admin_Console SHALL 展示该事件的规则匹配、规则执行与决策聚合的完整链路记录。
5. WHEN 风控运营人员打开 Admin_Console 的执行链路查询与监控页，THE Admin_Console SHALL 展示事件处理量、决策耗时与规则命中率的监控指标。

### Requirement 16: 性能与可用性（非功能需求）

**User Story:** 作为业务方系统，我想要平台在事中处理时具备低延迟与高可用，以便不阻塞交易主流程。

#### Acceptance Criteria

1. WHEN 业务方系统在事中处理中提交风控事件，THE Risk_Decision_Platform SHALL 在 P99 不超过 500 毫秒内返回最终决策。
2. WHILE 流计算服务或 ES 存储不可用，THE Decision_Engine SHALL 基于 Redis_Slice_Store 中已有的指标值继续产出事中决策。
3. IF 某个被规则引用的指标值不可读取，THEN THE Rule_Engine SHALL 依据该指标定义的默认取值策略进行求值并记录指标缺失。
4. THE Risk_Decision_Platform SHALL 支持后端服务水平扩展以提升事件处理吞吐量。

### Requirement 17: 安全与访问控制（非功能需求）

**User Story:** 作为平台安全负责人，我想要对配置与查询操作进行鉴权与审计，以便防止未授权访问与操作。

#### Acceptance Criteria

1. WHEN 任意用户访问管理前端的配置或查询接口，THE Risk_Decision_Platform SHALL 校验该用户的身份与访问权限。
2. IF 用户未通过身份校验或不具备所需权限，THEN THE Risk_Decision_Platform SHALL 拒绝该请求并返回未授权的错误信息。
3. WHEN 风控运营人员对事件类型、规则、规则组或指标定义执行创建、更新或删除操作，THE Risk_Decision_Platform SHALL 记录包含操作人、操作时间与操作内容的审计日志。
4. THE Risk_Decision_Platform SHALL 对落库与传输中的交易主体敏感数据进行加密保护。

