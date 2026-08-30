package com.riskplatform.gateway.domain;

import com.riskplatform.common.model.PagedResult;

/**
 * 订单查询端口（R10.4）。由基础设施层基于 MyBatis-Plus 分页查询 risk_order 实现。
 *
 * <p>实现需保证：按商户/事件类型/时间范围过滤、分页返回（每页 ≤200）、
 * 无结果返回空列表（而非 null）。条件合法性由应用服务在调用前校验。
 */
public interface OrderQueryStore {

    /**
     * 分页查询订单。
     *
     * @param query 已通过合法性校验的查询条件
     * @return 分页结果（无匹配时 data 为空列表）
     */
    PagedResult<RiskOrderView> query(OrderQuery query);
}
