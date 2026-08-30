package com.riskplatform.gateway.adapter.order;

import com.riskplatform.common.model.PagedResult;
import com.riskplatform.gateway.application.OrderQueryService;
import com.riskplatform.gateway.domain.OrderQuery;
import com.riskplatform.gateway.domain.RiskOrderView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 订单查询 REST 适配器（R10.4/R10.5）。
 *
 * <p>对外暴露 {@code GET /api/v1/orders}：按商户/事件类型/时间范围分页查询订单及最终决策。
 * 每页 ≤200 条；无结果返回空列表；未提供过滤条件或起始时间晚于结束时间时返回字段级校验错误
 * （由 {@code GlobalExceptionHandler} 映射为 400）。
 *
 * <p>注：{@link RequestParam} 均显式指定 {@code name}，避免依赖编译期保留参数名，
 * 保证在 MockMvc standaloneSetup 等场景下也能正确绑定。
 */
@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderQueryService orderQueryService;

    public OrderController(OrderQueryService orderQueryService) {
        this.orderQueryService = orderQueryService;
    }

    /**
     * 分页查询订单。
     *
     * @param merchantId    商户标识过滤（可选）
     * @param eventTypeCode 事件类型 code 过滤（可选）
     * @param startTimeMs   事件时间范围起始（毫秒时间戳，可选）
     * @param endTimeMs     事件时间范围结束（毫秒时间戳，可选）
     * @param page          页码（从 1 开始，默认 1）
     * @param pageSize      每页大小（1–200，默认 20）
     * @return 订单分页结果（无匹配时为空列表）
     */
    @GetMapping
    public PagedResult<RiskOrderView> query(
            @RequestParam(name = "merchantId", required = false) String merchantId,
            @RequestParam(name = "eventTypeCode", required = false) String eventTypeCode,
            @RequestParam(name = "startTimeMs", required = false) Long startTimeMs,
            @RequestParam(name = "endTimeMs", required = false) Long endTimeMs,
            @RequestParam(name = "page", required = false, defaultValue = "1") int page,
            @RequestParam(name = "pageSize", required = false, defaultValue = "20") int pageSize) {

        OrderQuery query = new OrderQuery(
                merchantId, eventTypeCode, startTimeMs, endTimeMs, page, pageSize);
        return orderQueryService.query(query);
    }
}
