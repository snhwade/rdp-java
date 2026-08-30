package com.riskplatform.gateway.adapter.order;

import com.riskplatform.common.model.PagedResult;
import com.riskplatform.gateway.application.BusinessOrderQueryService;
import com.riskplatform.gateway.domain.BusinessOrderQuery;
import com.riskplatform.gateway.domain.BusinessOrderSummaryView;
import com.riskplatform.gateway.domain.RiskOrderView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 订单维度查询：同一业务订单下的多次风控调用聚合。
 */
@RestController
@RequestMapping("/api/v1/business-orders")
public class BusinessOrderController {

    private final BusinessOrderQueryService queryService;

    public BusinessOrderController(BusinessOrderQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping
    public PagedResult<BusinessOrderSummaryView> query(
            @RequestParam(name = "businessOrderId", required = false) String businessOrderId,
            @RequestParam(name = "merchantId", required = false) String merchantId,
            @RequestParam(name = "eventTypeCode", required = false) String eventTypeCode,
            @RequestParam(name = "startTimeMs", required = false) Long startTimeMs,
            @RequestParam(name = "endTimeMs", required = false) Long endTimeMs,
            @RequestParam(name = "page", required = false, defaultValue = "1") int page,
            @RequestParam(name = "pageSize", required = false, defaultValue = "20") int pageSize) {
        return queryService.query(new BusinessOrderQuery(
                businessOrderId, merchantId, eventTypeCode, startTimeMs, endTimeMs, page, pageSize));
    }

    @GetMapping("/{businessOrderId}/invocations")
    public PagedResult<RiskOrderView> invocations(
            @PathVariable String businessOrderId,
            @RequestParam(name = "page", required = false, defaultValue = "1") int page,
            @RequestParam(name = "pageSize", required = false, defaultValue = "20") int pageSize) {
        return queryService.listInvocations(businessOrderId, page, pageSize);
    }
}
