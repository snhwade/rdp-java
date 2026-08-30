package com.riskplatform.bff.adapter;

import com.riskplatform.bff.application.BffAggregationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static com.riskplatform.bff.adapter.RuleConfigBffController.buildQuery;

/**
 * 查询与运营类页面 BFF 聚合接口（R14.1）。
 *
 * <p>为前端「决策结果/订单查询/筛查/商户评级/AI 训练/执行链路监控」页提供页面级接口，
 * 转发至对应后端服务并透传 JWT（R17.1）。
 */
@RestController
@RequestMapping("/bff/api/v1")
public class OpsBffController {

    private final BffAggregationService aggregation;

    public OpsBffController(BffAggregationService aggregation) {
        this.aggregation = aggregation;
    }

    private static String auth(HttpServletRequest request) {
        return request.getHeader(HttpHeaders.AUTHORIZATION);
    }

    // —— 订单查询（决策网关）——
    @GetMapping("/orders")
    public Object queryOrders(@RequestParam Map<String, String> params, HttpServletRequest request) {
        return aggregation.gatewayGet("/api/v1/orders" + buildQuery(params), auth(request));
    }

    // —— 统一决策查询（引擎 + AI 摘要）——
    @GetMapping("/decision-records")
    public Object queryDecisionRecords(@RequestParam Map<String, String> params, HttpServletRequest request) {
        return aggregation.gatewayGet("/api/v1/decision-records" + buildQuery(params), auth(request));
    }

    @GetMapping("/decision-records/{eventId}")
    public Object getDecisionRecordDetail(@PathVariable("eventId") String eventId, HttpServletRequest request) {
        return aggregation.gatewayGet("/api/v1/decision-records/" + eventId, auth(request));
    }

    @GetMapping("/business-orders")
    public Object queryBusinessOrders(@RequestParam Map<String, String> params, HttpServletRequest request) {
        return aggregation.gatewayGet("/api/v1/business-orders" + buildQuery(params), auth(request));
    }

    @GetMapping("/business-orders/{businessOrderId}/invocations")
    public Object listBusinessOrderInvocations(
            @PathVariable("businessOrderId") String businessOrderId,
            @RequestParam Map<String, String> params,
            HttpServletRequest request) {
        return aggregation.gatewayGet(
                "/api/v1/business-orders/" + businessOrderId + "/invocations" + buildQuery(params),
                auth(request));
    }

    @GetMapping("/engine-decision-records")
    public Object queryEngineDecisionRecords(@RequestParam Map<String, String> params, HttpServletRequest request) {
        return aggregation.gatewayGet("/api/v1/engine-decision-records" + buildQuery(params), auth(request));
    }

    @GetMapping("/engine-decision-records/{eventId}")
    public Object getEngineDecisionRecord(@PathVariable("eventId") String eventId, HttpServletRequest request) {
        return aggregation.gatewayGet("/api/v1/engine-decision-records/" + eventId, auth(request));
    }

    @GetMapping("/ai-decision-records/stats")
    public Object aiDecisionStats(@RequestParam Map<String, String> params, HttpServletRequest request) {
        return aggregation.gatewayGet("/api/v1/ai-decision-records/stats" + buildQuery(params), auth(request));
    }

    @GetMapping("/ai-decision-records")
    public Object queryAiDecisionRecords(@RequestParam Map<String, String> params, HttpServletRequest request) {
        return aggregation.gatewayGet("/api/v1/ai-decision-records" + buildQuery(params), auth(request));
    }

    @GetMapping("/ai-decision-records/{eventId}")
    public Object getAiDecisionRecord(@PathVariable("eventId") String eventId, HttpServletRequest request) {
        return aggregation.gatewayGet("/api/v1/ai-decision-records/" + eventId, auth(request));
    }

    @GetMapping("/agent/runtime")
    public Object getAgentRuntime(HttpServletRequest request) {
        return aggregation.gatewayGet("/api/v1/agent/runtime", auth(request));
    }

    // —— 决策结果 / 执行链路（引擎）——
    @GetMapping("/decisions/{eventId}")
    public Object getDecision(@PathVariable("eventId") String eventId, HttpServletRequest request) {
        return aggregation.engineGet("/api/v1/decisions/" + eventId, auth(request));
    }

    @GetMapping("/decision-records/{eventId}/trace")
    public Object getDecisionRecordTrace(@PathVariable("eventId") String eventId, HttpServletRequest request) {
        return aggregation.gatewayGet("/api/v1/decision-records/" + eventId + "/trace", auth(request));
    }

    @GetMapping("/engine-decision-records/stats")
    public Object engineDecisionStats(@RequestParam Map<String, String> params, HttpServletRequest request) {
        return aggregation.gatewayGet("/api/v1/engine-decision-records/stats" + buildQuery(params), auth(request));
    }

    @GetMapping("/trace/{eventId}")
    public Object getTrace(@PathVariable("eventId") String eventId, HttpServletRequest request) {
        return aggregation.gatewayGet("/api/v1/decision-records/" + eventId + "/trace", auth(request));
    }

    // —— 筛查 ——
    @PostMapping("/screening")
    public Object screen(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        return aggregation.screeningPost("/api/v1/screening", body, auth(request));
    }

    @PutMapping("/screening/threshold")
    public Object updateScreeningThreshold(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        return aggregation.screeningPut("/api/v1/screening/threshold", body, auth(request));
    }

    // —— 商户评级 ——
    @GetMapping("/merchant-ratings")
    public Object listMerchantRatings(@RequestParam Map<String, String> params, HttpServletRequest request) {
        return aggregation.merchantRatingGet("/api/v1/merchant-ratings" + buildQuery(params), auth(request));
    }

    @PostMapping("/merchants/{id}/rating")
    public Object computeRating(@PathVariable("id") String id,
                                @RequestBody(required = false) Map<String, Object> body,
                                HttpServletRequest request) {
        return aggregation.merchantRatingPost("/api/v1/merchants/" + id + "/rating", body, auth(request));
    }

    @GetMapping("/merchants/{id}/rating")
    public Object getRating(@PathVariable("id") String id, HttpServletRequest request) {
        return aggregation.merchantRatingGet("/api/v1/merchants/" + id + "/rating", auth(request));
    }

    // —— AI 训练任务 ——
    @PostMapping("/ai/training-jobs")
    public Object submitTrainingJob(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        return aggregation.aiTrainingPost("/api/v1/ai/training-jobs", body, auth(request));
    }

    @GetMapping("/ai/training-jobs")
    public Object listTrainingJobs(@RequestParam Map<String, String> params, HttpServletRequest request) {
        return aggregation.aiTrainingGet("/api/v1/ai/training-jobs" + buildQuery(params), auth(request));
    }

    @GetMapping("/ai/training-schedules")
    public Object listTrainingSchedules(HttpServletRequest request) {
        return aggregation.aiTrainingGet("/api/v1/ai/training-schedules", auth(request));
    }

    @PostMapping("/ai/training-schedules")
    public Object createTrainingSchedule(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        return aggregation.aiTrainingPost("/api/v1/ai/training-schedules", body, auth(request));
    }

    @PutMapping("/ai/training-schedules/{id}")
    public Object updateTrainingSchedule(@PathVariable("id") long id,
                                         @RequestBody Map<String, Object> body,
                                         HttpServletRequest request) {
        return aggregation.aiTrainingPut("/api/v1/ai/training-schedules/" + id, body, auth(request));
    }

    @DeleteMapping("/ai/training-schedules/{id}")
    public Object deleteTrainingSchedule(@PathVariable("id") long id, HttpServletRequest request) {
        return aggregation.aiTrainingDelete("/api/v1/ai/training-schedules/" + id, auth(request));
    }

    @PostMapping("/ai/training-schedules/{id}/run-now")
    public Object runTrainingScheduleNow(@PathVariable("id") long id, HttpServletRequest request) {
        return aggregation.aiTrainingPost("/api/v1/ai/training-schedules/" + id + "/run-now", Map.of(), auth(request));
    }

    // —— AI 模型管理 ——
    @GetMapping("/ai/models")
    public Object listAiModels(HttpServletRequest request) {
        return aggregation.aiTrainingGet("/api/v1/ai/models", auth(request));
    }

    @GetMapping("/ai/models/{kind}")
    public Object getAiModel(@PathVariable("kind") String kind, HttpServletRequest request) {
        return aggregation.aiTrainingGet("/api/v1/ai/models/" + kind, auth(request));
    }

    @PutMapping("/ai/models/{kind}/current")
    public Object activateAiModel(@PathVariable("kind") String kind,
                                  @RequestBody Map<String, Object> body,
                                  HttpServletRequest request) {
        return aggregation.aiTrainingPut("/api/v1/ai/models/" + kind + "/current", body, auth(request));
    }

    @PutMapping("/ai/models/{kind}")
    public Object updateAiModelMeta(@PathVariable("kind") String kind,
                                    @RequestBody Map<String, Object> body,
                                    HttpServletRequest request) {
        return aggregation.aiTrainingPut("/api/v1/ai/models/" + kind, body, auth(request));
    }

    @PostMapping("/ai/score")
    public Object probeAiScore(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        return aggregation.aiTrainingPost("/api/v1/ai/score", body, auth(request));
    }
}
