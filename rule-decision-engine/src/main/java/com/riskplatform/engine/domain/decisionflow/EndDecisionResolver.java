package com.riskplatform.engine.domain.decisionflow;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

/**
 * 结束节点决策结果解析器（R9.7）。
 *
 * <p>决策流执行到达某 END（结束）节点时，应产出该节点<strong>配置</strong>的决策结果作为整条决策流的
 * 结果。该配置随画布保存序列化进节点级配置 JSON（{@link DecisionFlowDef.Node#config()}），键为
 * {@code endDecision}，取值为 {@code REFUND}/{@code MANUAL_REVIEW}/{@code AUTO_PASS}/{@code AUTO_REJECT}
 * 之一（与配置侧画布保存、前端 {@code EndDecision} 类型对齐）。
 *
 * <p>本解析器从 END 节点配置中取出该值：
 * <ul>
 *   <li>配置存在且 {@code endDecision} 非空 → 返回其字符串值（去除首尾空白）；</li>
 *   <li>配置为空、解析失败或未含 {@code endDecision} → 返回 {@code null}，调用方据此回退到命中聚合决策，
 *       以兼容历史上未配置结束决策结果的决策流数据。</li>
 * </ul>
 */
public final class EndDecisionResolver {

    /** END 节点配置中承载结束决策结果的键（与配置侧/前端约定一致）。 */
    private static final String CONFIG_KEY = "endDecision";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private EndDecisionResolver() {
    }

    /**
     * 从 END 节点配置 JSON 解析其配置的决策结果。
     *
     * @param config END 节点的配置 JSON（可空）
     * @return 配置的决策结果（如 REFUND/MANUAL_REVIEW/AUTO_PASS/AUTO_REJECT）；未配置或解析失败时为 {@code null}
     */
    public static String resolve(String config) {
        if (config == null || config.isBlank()) {
            return null;
        }
        try {
            Map<?, ?> parsed = MAPPER.readValue(config, Map.class);
            Object value = parsed == null ? null : parsed.get(CONFIG_KEY);
            if (value == null) {
                return null;
            }
            String text = String.valueOf(value).trim();
            return text.isEmpty() ? null : text;
        } catch (Exception e) {
            // 配置非法 JSON 等：按未配置处理，由调用方回退到聚合决策
            return null;
        }
    }
}
