package com.riskplatform.engine.infrastructure.rulepackage;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 策略定义只读持久化对象（对应 strategy_def 表，V16，扩展阶段 R6.2）。
 *
 * <p>在线决策面规则包节点加载命中规则/区间映射所绑定的策略时读取。仅读取，不修改。
 * limitType/threshold/通知渠道等具体参数存于 {@code params_json}。
 */
@TableName("strategy_def")
public class StrategyDefReadPO {

    @TableId(type = IdType.AUTO)
    private Long id;
    /** VERIFY/CONTROL_STATE/CONTROL_LIMIT/NOTIFY/LISTING */
    private String category;
    private String code;
    private String name;
    private String paramsJson;
    private String status;

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
}
