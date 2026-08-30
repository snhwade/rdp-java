package com.riskplatform.engine.domain.model;

/**
 * 模型在线评分结果（扩展阶段 R6.4）。
 *
 * <p>承载一次模型评分的产出：是否可用、模型分值、（可选）模型给出的标签/等级、不可用原因。
 * 模型不可用时 {@link #available} 为 false，{@link #score} 无意义（由节点降级配置决定默认值），
 * {@link #reason} 记录不可用原因供链路追溯（R6.4「记录原因」）。
 *
 * @param available 模型是否可用并成功产出评分
 * @param score     模型分值（available=true 时有效）
 * @param label     模型输出标签/等级（可空，如欺诈高/中/低）
 * @param reason    不可用原因（available=false 时给出；可用时为 null）
 */
public record ModelScoreResult(boolean available, double score, String label, String reason) {

    /** 构造一个可用结果。 */
    public static ModelScoreResult ok(double score, String label) {
        return new ModelScoreResult(true, score, label, null);
    }

    /** 构造一个不可用结果（附原因），供模型节点按降级策略处理。 */
    public static ModelScoreResult unavailable(String reason) {
        return new ModelScoreResult(false, 0.0, null, reason);
    }
}
