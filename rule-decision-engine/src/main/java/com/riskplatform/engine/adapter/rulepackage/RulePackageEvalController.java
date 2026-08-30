package com.riskplatform.engine.adapter.rulepackage;

import com.riskplatform.common.error.BizException;
import com.riskplatform.common.error.CommonErrorCode;
import com.riskplatform.engine.domain.rulepackage.RulePackageDefinition;
import com.riskplatform.engine.domain.rulepackage.RulePackageDefinitionPort;
import com.riskplatform.engine.domain.rulepackage.RulePackageExecutor;
import com.riskplatform.engine.domain.rulepackage.RulePackageResult;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 规则包运行时调用（「规则包调用」）。
 *
 * <p>{@code POST /api/v1/rule-packages/{id}/evaluate}：按规则包 id 加载配置并执行，
 * 供业务系统/决策网关直接调用。
 */
@RestController
@RequestMapping("/api/v1/rule-packages")
public class RulePackageEvalController {

    private final RulePackageDefinitionPort definitionPort;
    private final RulePackageExecutor rulePackageExecutor;

    public RulePackageEvalController(RulePackageDefinitionPort definitionPort,
                                     @Qualifier("onlineRulePackageExecutor")
                                     RulePackageExecutor rulePackageExecutor) {
        this.definitionPort = definitionPort;
        this.rulePackageExecutor = rulePackageExecutor;
    }

    @PostMapping("/{id}/evaluate")
    public RulePackageEvaluateResponse evaluate(@PathVariable("id") long packageId,
                                               @RequestBody EvaluateRequest request) {
        RulePackageDefinition definition = definitionPort.load(packageId);
        if (definition == null) {
            throw new BizException(CommonErrorCode.NOT_FOUND,
                    "规则包不存在或已下线",
                    Map.of("rulePackageId", String.valueOf(packageId)));
        }
        Map<String, Object> context = request.context() == null ? Map.of() : request.context();
        long started = System.currentTimeMillis();
        RulePackageResult result = rulePackageExecutor.execute(definition, context);
        return RulePackageEvaluateResponse.from(
                request.eventId(),
                packageId,
                result,
                System.currentTimeMillis() - started);
    }

    public record EvaluateRequest(String eventId, Map<String, Object> context) {
    }

    public record HitView(long ruleId, String decision, boolean trialRun) {
        static List<HitView> from(RulePackageResult result) {
            return result.hitRules().stream()
                    .map(h -> new HitView(h.ruleId(), h.decision().name(), h.trialRun()))
                    .toList();
        }
    }

    public record RulePackageEvaluateResponse(
            String eventId,
            long rulePackageId,
            String triggerMode,
            String decision,
            List<HitView> hits,
            String score,
            String riskLevelCode,
            boolean warnGenerated,
            long elapsedMs) {

        static RulePackageEvaluateResponse from(String eventId,
                                                long packageId,
                                                RulePackageResult result,
                                                long elapsedMs) {
            return new RulePackageEvaluateResponse(
                    eventId,
                    packageId,
                    result.triggerMode().name(),
                    result.decision().name(),
                    HitView.from(result),
                    result.score() == null ? null : result.score().toPlainString(),
                    result.riskLevelCode(),
                    result.warnGenerated(),
                    elapsedMs);
        }
    }
}
