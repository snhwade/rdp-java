package com.riskplatform.engine.infrastructure.decisiontool;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/** scorecard 只读 PO。 */
@TableName("scorecard")
public class ScorecardReadPO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String variablesJson;
    private String levelsJson;
    private String status;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getVariablesJson() {
        return variablesJson;
    }

    public void setVariablesJson(String variablesJson) {
        this.variablesJson = variablesJson;
    }

    public String getLevelsJson() {
        return levelsJson;
    }

    public void setLevelsJson(String levelsJson) {
        this.levelsJson = levelsJson;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
