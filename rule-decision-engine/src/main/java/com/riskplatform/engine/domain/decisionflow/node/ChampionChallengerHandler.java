package com.riskplatform.engine.domain.decisionflow.node;

import com.riskplatform.engine.domain.decisionflow.DecisionFlowDef;

/**
 * 冠军挑战（分流）节点处理器（扩展阶段 10.2，R8.1/8.2）。
 *
 * <p>冠军挑战节点用于流量分流 A/B 测试：与网关一样属于<b>纯路径节点</b>，本身不执行决策资产、
 * 不产命中决策、不产赋值字段，故 {@link #handle} 始终返回 {@link NodeResult#empty()}。
 *
 * <p>真正的「选路」在
 * {@link com.riskplatform.engine.domain.decisionflow.DecisionFlowEngine#evaluate} 的遍历器
 * {@code championChallengerNext} 中完成：按各出线的 {@code trafficPercent} 做<b>加权随机</b>选边
 * （累积区间 + 单次随机数），大数下各路径被选中频率趋近其配置百分比（R8.2，design Property 5）。
 * 出线 {@code trafficPercent} 之和须 = 100，由配置面保存期校验（R8.1）。
 *
 * <p>之所以仍注册显式处理器（而非依赖注册表未注册兜底），是为了让冠军挑战类型「被识别且语义明确」，
 * 并在 {@link NodeHandlerRegistry#supports} 中返回 true，便于后续扩展与排查。
 */
public final class ChampionChallengerHandler implements NodeHandler {

    @Override
    public DecisionFlowDef.NodeType supportedType() {
        return DecisionFlowDef.NodeType.CHAMPION_CHALLENGER;
    }

    @Override
    public NodeResult handle(DecisionFlowDef.Node node, FlowContext ctx) {
        // 冠军挑战仅作分流路径节点，不产决策；加权随机选路在引擎遍历器中完成
        return NodeResult.empty();
    }
}
