package com.riskplatform.bff.application;

import com.riskplatform.bff.domain.DownstreamClient;
import com.riskplatform.bff.infrastructure.config.DownstreamProperties;

/**
 * BFF 页面级聚合服务（R14.1/R17.1）。
 *
 * <p>将 Admin_Console 各页面所需的后端能力按下游服务路由转发，统一透传 JWT。
 * 当前阶段为「按服务转发 + 错误透传」的薄聚合；后续可在此组合多下游响应为页面级聚合视图。
 *
 * <p>各方法返回下游响应体（已由 {@link DownstreamClient} 反序列化）；下游错误经
 * {@link DownstreamClient.DownstreamException} 向上抛出，由适配层映射为统一错误体（R14.2）。
 */
public class BffAggregationService {

    private final DownstreamClient client;
    private final DownstreamProperties downstream;

    public BffAggregationService(DownstreamClient client, DownstreamProperties downstream) {
        this.client = client;
        this.downstream = downstream;
    }

    // —— 规则配置服务（事件类型/规则/规则组/选择器/指标定义/决策优先级）——

    public Object ruleConfigGet(String path, String authorization) {
        return client.get(downstream.getRuleConfig(), path, authorization);
    }

    public Object ruleConfigPost(String path, Object body, String authorization) {
        return client.post(downstream.getRuleConfig(), path, body, authorization);
    }

    public Object ruleConfigPut(String path, Object body, String authorization) {
        return client.put(downstream.getRuleConfig(), path, body, authorization);
    }

    public Object ruleConfigDelete(String path, String authorization) {
        return client.delete(downstream.getRuleConfig(), path, authorization);
    }

    // —— 决策网关（订单查询）——

    public Object gatewayGet(String path, String authorization) {
        return client.get(downstream.getDecisionGateway(), path, authorization);
    }

    // —— 规则/决策引擎（决策结果、执行链路、试运行、决策流执行链路）——

    public Object engineGet(String path, String authorization) {
        return client.get(downstream.getRuleDecisionEngine(), path, authorization);
    }

    public Object enginePost(String path, Object body, String authorization) {
        return client.post(downstream.getRuleDecisionEngine(), path, body, authorization);
    }

    // —— 指标存储 ——

    public Object indicatorGet(String path, String authorization) {
        return client.get(downstream.getIndicatorStore(), path, authorization);
    }

    // —— 筛查服务 ——

    public Object screeningPost(String path, Object body, String authorization) {
        return client.post(downstream.getScreening(), path, body, authorization);
    }

    public Object screeningPut(String path, Object body, String authorization) {
        return client.put(downstream.getScreening(), path, body, authorization);
    }

    public Object screeningGet(String path, String authorization) {
        return client.get(downstream.getScreening(), path, authorization);
    }

    public Object screeningDelete(String path, String authorization) {
        return client.delete(downstream.getScreening(), path, authorization);
    }

    // —— 商户评级 ——

    public Object merchantRatingGet(String path, String authorization) {
        return client.get(downstream.getMerchantRating(), path, authorization);
    }

    public Object merchantRatingPost(String path, Object body, String authorization) {
        return client.post(downstream.getMerchantRating(), path, body, authorization);
    }

    // —— AI 训练服务 ——

    public Object aiTrainingGet(String path, String authorization) {
        return client.get(downstream.getAiTraining(), path, authorization);
    }

    public Object aiTrainingPost(String path, Object body, String authorization) {
        return client.post(downstream.getAiTraining(), path, body, authorization);
    }

    public Object aiTrainingPut(String path, Object body, String authorization) {
        return client.put(downstream.getAiTraining(), path, body, authorization);
    }

    public Object aiTrainingDelete(String path, String authorization) {
        return client.delete(downstream.getAiTraining(), path, authorization);
    }
}
