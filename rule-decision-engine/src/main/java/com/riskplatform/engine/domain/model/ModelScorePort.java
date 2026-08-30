package com.riskplatform.engine.domain.model;

import java.util.Map;

/**
 * 模型在线评分端口（扩展阶段 R6.4）。
 *
 * <p>领域层定义、基础设施层实现：决策流「模型节点」据此调用所引用模型（如 AI 欺诈评分）产出模型输出值。
 * 实现方（{@code AiScoreClient}）经 HTTP 调用 ai-training-service 在线评分端点。
 *
 * <p><b>降级语义（R6.4）</b>：模型不可用（端点未实现/超时/异常）时，实现方<strong>不抛异常</strong>，
 * 而是返回 {@link ModelScoreResult#unavailable(String)} 标记不可用并附原因，由
 * {@code ModelNodeHandler} 按节点配置的降级策略处理（产出默认值并记录原因）。
 */
public interface ModelScorePort {

    /**
     * 调用模型在线评分。
     *
     * @param modelRef 模型引用标识（节点 refType/refId 解析得到的模型编码或 id 字符串）
     * @param features 评分特征（决策上下文求值环境的视图）
     * @return 评分结果；不可用时返回 {@link ModelScoreResult#unavailable(String)}
     */
    ModelScoreResult score(String modelRef, Map<String, Object> features);
}
