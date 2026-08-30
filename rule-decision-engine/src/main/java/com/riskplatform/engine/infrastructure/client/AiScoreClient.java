package com.riskplatform.engine.infrastructure.client;

import com.riskplatform.engine.domain.model.ModelScorePort;
import com.riskplatform.engine.domain.model.ModelScoreResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.Map;

/**
 * AI 在线评分客户端（引擎 → ai-training-service，扩展阶段 R6.4）。
 *
 * <p>实现 {@link ModelScorePort}：决策流「模型节点」调用所引用模型在线评分。经 HTTP POST 调用
 * ai-training-service 的在线评分端点，请求体为 {@code { modelRef, features }}，期望响应体形如
 * {@code { score: number, label?: string }}。
 *
 * <h3>AI 端点</h3>
 * <p>调用 ai-training-service {@code POST /api/v1/ai/score}。响应含 {@code available}/{@code score}/{@code label}；
 * {@code available=false} 或缺少 score 时返回 {@link ModelScoreResult#unavailable(String)}，由
 * {@code ModelNodeHandler} 按节点降级配置处理（R6.4）。
 *
 * <p><b>不抛异常</b>：任何 HTTP/解析异常都被捕获并转为「不可用」结果，确保在线决策链路不被模型不可用阻断。
 */
public class AiScoreClient implements ModelScorePort {

    private static final Logger log = LoggerFactory.getLogger(AiScoreClient.class);

    /** 在线评分端点路径（约定；ai-training-service 端点上线后即生效）。 */
    private static final String SCORE_PATH = "/api/v1/ai/score";

    private final RestClient restClient;
    private final String baseUrl;

    public AiScoreClient(RestClient restClient, String baseUrl) {
        this.restClient = restClient;
        this.baseUrl = baseUrl;
    }

    @Override
    @SuppressWarnings("unchecked")
    public ModelScoreResult score(String modelRef, Map<String, Object> features) {
        try {
            Map<String, Object> request = new HashMap<>();
            request.put("modelRef", modelRef);
            request.put("features", features == null ? Map.of() : features);

            Map<String, Object> resp = restClient.post()
                    .uri(baseUrl + SCORE_PATH)
                    .body(request)
                    .retrieve()
                    .body(Map.class);

            if (resp == null) {
                return ModelScoreResult.unavailable("AI 评分响应为空");
            }
            if (Boolean.FALSE.equals(resp.get("available"))) {
                Object reason = resp.get("reason");
                return ModelScoreResult.unavailable(
                        reason == null ? "AI 模型不可用" : String.valueOf(reason));
            }
            if (!(resp.get("score") instanceof Number scoreNum)) {
                return ModelScoreResult.unavailable(
                        "AI 评分响应缺少 score 字段（端点可能尚未实现在线评分）");
            }
            Object labelObj = resp.get("label");
            String label = labelObj == null ? null : String.valueOf(labelObj);
            return ModelScoreResult.ok(scoreNum.doubleValue(), label);
        } catch (Exception ex) {
            // AI 端点不可用（未实现/超时/异常）：转为「不可用」结果，由节点按降级策略处理（R6.4）
            log.warn("AI 在线评分不可用，按降级处理: modelRef={} 原因={}", modelRef, ex.getMessage());
            return ModelScoreResult.unavailable("AI 在线评分调用失败: " + ex.getMessage());
        }
    }
}
