package com.riskplatform.ruleconfig.infrastructure.scenario;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * scenario_event 表持久化对象（V13）：场景-事件多对多关联。
 */
@TableName("scenario_event")
public class ScenarioEventPO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long scenarioId;
    private String eventTypeCode;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getScenarioId() {
        return scenarioId;
    }

    public void setScenarioId(Long scenarioId) {
        this.scenarioId = scenarioId;
    }

    public String getEventTypeCode() {
        return eventTypeCode;
    }

    public void setEventTypeCode(String eventTypeCode) {
        this.eventTypeCode = eventTypeCode;
    }
}
