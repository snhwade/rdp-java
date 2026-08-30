package com.riskplatform.ruleconfig.infrastructure.strategy;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * strategy_def 表持久化对象（V16）。
 *
 * <p>审计列 create_time/update_time 由数据库默认值与 ON UPDATE 维护，
 * create_user/update_user 暂留空（与 V16 schema 一致）。
 */
@TableName("strategy_def")
public class StrategyDefPO {

    @TableId(type = IdType.AUTO)
    private Long id;
    /** VERIFY/CONTROL_STATE/CONTROL_LIMIT/NOTIFY/LISTING */
    private String category;
    private String code;
    private String name;
    private String paramsJson;
    /** ENABLED / DISABLED */
    private String status;
    /** 验证策略优先级 1..9999 越小优先级越高（risk-console-redesign R5.5）。非验证类为空。 */
    private Integer priority;
    /** 作用域场景ID（NULL + anyScope=1 表示不限业务场景，R5.4）。 */
    private Long scopeScenarioId;
    /** 是否不限业务场景 0/1（R5.4）。 */
    private Boolean anyScope;
    private String createUser;
    private LocalDateTime createTime;
    private String updateUser;
    private LocalDateTime updateTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getParamsJson() {
        return paramsJson;
    }

    public void setParamsJson(String paramsJson) {
        this.paramsJson = paramsJson;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getPriority() {
        return priority;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
    }

    public Long getScopeScenarioId() {
        return scopeScenarioId;
    }

    public void setScopeScenarioId(Long scopeScenarioId) {
        this.scopeScenarioId = scopeScenarioId;
    }

    public Boolean getAnyScope() {
        return anyScope;
    }

    public void setAnyScope(Boolean anyScope) {
        this.anyScope = anyScope;
    }

    public String getCreateUser() {
        return createUser;
    }

    public void setCreateUser(String createUser) {
        this.createUser = createUser;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public String getUpdateUser() {
        return updateUser;
    }

    public void setUpdateUser(String updateUser) {
        this.updateUser = updateUser;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }
}
