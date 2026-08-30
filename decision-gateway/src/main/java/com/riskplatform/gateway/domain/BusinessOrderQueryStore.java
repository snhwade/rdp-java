package com.riskplatform.gateway.domain;

import com.riskplatform.common.model.PagedResult;

/**
 * 订单维度查询端口。
 */
public interface BusinessOrderQueryStore {

    PagedResult<BusinessOrderSummaryView> querySummaries(BusinessOrderQuery query);

    PagedResult<RiskOrderView> listInvocations(String businessOrderId, int page, int pageSize);
}
