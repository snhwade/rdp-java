package com.riskplatform.engine.domain.decisionflow;

/**
 * 子决策流定义加载端口（在线决策面，扩展阶段 R8.5）。
 *
 * <p>领域层定义、基础设施层实现：按决策流 id 从配置侧（rule-config 拥有的 {@code decision_flow} 表，
 * 引擎共享同一库）加载该决策流的节点/边定义，统一为 {@link DecisionFlowDef} 供
 * {@link DecisionFlowEngine} 递归执行（子决策流节点用，R8.5）。
 *
 * <p>与 {@code RulePackageDefinitionPort} 同款只读 DAO 思路（参考 6.2/7.2）：只读 {@code decision_flow}
 * 表的 {@code nodes_json}/{@code edges_json}/{@code start_node_id}，不修改任何配置。
 *
 * <p>注意：子流程内若含「决策工具节点」（决策表/评分卡/决策树/决策矩阵），其内联定义映射当前置空
 * （仅决策流执行入口才注入内联工具定义）；此类节点在子流程内按运行期降级处理（空结果，不中断）。
 * 这是当前阶段的已知边界，规则包/模型/网关/分流/子流程节点不受影响。
 */
public interface SubFlowDefinitionPort {

    /**
     * 加载子决策流执行定义。
     *
     * @param flowId 子决策流 id
     * @return 决策流执行定义；流程不存在/已下线时返回 {@code null}
     *         （由调用方按运行期降级处理 R8.4）
     */
    DecisionFlowDef load(long flowId);
}
