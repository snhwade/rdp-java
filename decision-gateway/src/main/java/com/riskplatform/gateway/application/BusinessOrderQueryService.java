package com.riskplatform.gateway.application;

import com.riskplatform.common.error.ValidationException;
import com.riskplatform.common.model.PagedResult;
import com.riskplatform.gateway.domain.BusinessOrderQuery;
import com.riskplatform.gateway.domain.BusinessOrderQueryStore;
import com.riskplatform.gateway.domain.BusinessOrderSummaryView;
import com.riskplatform.gateway.domain.RiskOrderView;

/**
 * 订单维度查询（按业务订单号聚合多次调用）。
 */
public class BusinessOrderQueryService {

    private final BusinessOrderQueryStore store;

    public BusinessOrderQueryService(BusinessOrderQueryStore store) {
        this.store = store;
    }

    public PagedResult<BusinessOrderSummaryView> query(BusinessOrderQuery query) {
        ValidationException.Builder errors = ValidationException.builder();
        if (query.isTimeRangeInverted()) {
            errors.field("timeRange", "起始时间不得晚于结束时间");
        }
        if (query.page() < 1) {
            errors.field("page", "页码须从 1 开始");
        }
        if (query.pageSize() < 1 || query.pageSize() > 200) {
            errors.field("pageSize", "每页大小须在 1–200 之间");
        }
        errors.throwIfAny();
        return store.querySummaries(query);
    }

    public PagedResult<RiskOrderView> listInvocations(String businessOrderId, int page, int pageSize) {
        return store.listInvocations(businessOrderId, page, pageSize);
    }
}
