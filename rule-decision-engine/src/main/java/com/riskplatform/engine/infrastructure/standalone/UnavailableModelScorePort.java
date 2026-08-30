package com.riskplatform.engine.infrastructure.standalone;

import com.riskplatform.engine.domain.model.ModelScorePort;
import com.riskplatform.engine.domain.model.ModelScoreResult;

import java.util.Map;

/** standalone 模式：不调用 ai-training HTTP，模型节点统一降级。 */
public class UnavailableModelScorePort implements ModelScorePort {

    @Override
    public ModelScoreResult score(String modelRef, Map<String, Object> features) {
        return ModelScoreResult.unavailable("standalone 模式未启用 AI 在线评分服务");
    }
}
