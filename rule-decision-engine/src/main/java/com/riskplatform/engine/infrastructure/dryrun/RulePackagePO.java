package com.riskplatform.engine.infrastructure.dryrun;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;

/**
 * 规则包只读持久化对象（对应 rule_package 表，V14，R5.2）。
 *
 * <p>试运行加载目标定义时读取触发模式与预警单阈值配置。仅读取，不修改。
 */
@TableName("rule_package")
public class RulePackagePO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String code;
    private String name;
    private String triggerMode;
    private String status;
    private Integer warnScoreEnabled;
    private String warnScoreOp;
    private BigDecimal warnScoreThreshold;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public String getTriggerMode() {
        return triggerMode;
    }

    public void setTriggerMode(String triggerMode) {
        this.triggerMode = triggerMode;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getWarnScoreEnabled() {
        return warnScoreEnabled;
    }

    public void setWarnScoreEnabled(Integer warnScoreEnabled) {
        this.warnScoreEnabled = warnScoreEnabled;
    }

    public String getWarnScoreOp() {
        return warnScoreOp;
    }

    public void setWarnScoreOp(String warnScoreOp) {
        this.warnScoreOp = warnScoreOp;
    }

    public BigDecimal getWarnScoreThreshold() {
        return warnScoreThreshold;
    }

    public void setWarnScoreThreshold(BigDecimal warnScoreThreshold) {
        this.warnScoreThreshold = warnScoreThreshold;
    }
}
