package com.riskplatform.ruleconfig.domain.eventtype;

/**
 * 事件引擎可执行状态查询端口（risk-console-redesign R2.11）。
 *
 * <p>用于查询某事件在引擎服务中的可执行状态：引擎判定一个事件是否「可执行」的依据是
 * 该事件下是否存在可被加载执行的配置（启用的规则/规则包/决策流等）。本服务通过该端口
 * 解耦具体查询方式（REST 调用引擎、或基于本地配置推断）。
 */
public interface EventEngineStatusQuery {

    /** 引擎可执行状态。 */
    enum Status {
        /** 引擎可执行该事件（存在可加载执行的配置）。 */
        EXECUTABLE,
        /** 引擎暂不可执行（无可加载执行的配置）。 */
        NOT_EXECUTABLE,
        /** 引擎不可达 / 状态未知（降级）。 */
        UNKNOWN
    }

    /**
     * 查询指定事件在引擎中的可执行状态。
     *
     * @param eventCode 事件 code
     * @return 引擎可执行状态
     */
    Status query(String eventCode);
}
