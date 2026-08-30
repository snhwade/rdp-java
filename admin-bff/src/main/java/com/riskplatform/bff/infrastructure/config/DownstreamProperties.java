package com.riskplatform.bff.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 下游服务地址配置（绑定 application.yml 的 {@code downstream.*}）。
 *
 * <p>BFF 据此将页面级聚合请求转发到对应后端服务（R14.1）。
 */
@ConfigurationProperties(prefix = "downstream")
public class DownstreamProperties {

    private String ruleConfig;
    private String ruleDecisionEngine;
    private String indicatorStore;
    private String screening;
    private String merchantRating;
    private String decisionGateway;
    private String aiTraining;

    public String getRuleConfig() {
        return ruleConfig;
    }

    public void setRuleConfig(String ruleConfig) {
        this.ruleConfig = ruleConfig;
    }

    public String getRuleDecisionEngine() {
        return ruleDecisionEngine;
    }

    public void setRuleDecisionEngine(String ruleDecisionEngine) {
        this.ruleDecisionEngine = ruleDecisionEngine;
    }

    public String getIndicatorStore() {
        return indicatorStore;
    }

    public void setIndicatorStore(String indicatorStore) {
        this.indicatorStore = indicatorStore;
    }

    public String getScreening() {
        return screening;
    }

    public void setScreening(String screening) {
        this.screening = screening;
    }

    public String getMerchantRating() {
        return merchantRating;
    }

    public void setMerchantRating(String merchantRating) {
        this.merchantRating = merchantRating;
    }

    public String getDecisionGateway() {
        return decisionGateway;
    }

    public void setDecisionGateway(String decisionGateway) {
        this.decisionGateway = decisionGateway;
    }

    public String getAiTraining() {
        return aiTraining;
    }

    public void setAiTraining(String aiTraining) {
        this.aiTraining = aiTraining;
    }
}
