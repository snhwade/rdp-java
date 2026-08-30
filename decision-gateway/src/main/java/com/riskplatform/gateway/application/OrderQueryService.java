package com.riskplatform.gateway.application;

import com.riskplatform.common.error.ValidationException;
import com.riskplatform.common.model.PagedResult;
import com.riskplatform.gateway.domain.OrderQuery;
import com.riskplatform.gateway.domain.OrderQueryStore;
import com.riskplatform.gateway.domain.RiskOrderView;

/**
 * 订单查询应用服务（R10.4/R10.5）。
 *
 * <p>职责：
 * <ul>
 *   <li>校验查询条件：未提供任何过滤条件、或起始时间晚于结束时间时拒绝并返回字段级错误（R10.5）；</li>
 *   <li>规整分页参数：页码 ≥1、每页大小落在 [1, 200]（R10.4，超限拒绝）；</li>
 *   <li>委派持久化端口分页查询，无结果返回空列表（R10.4）。</li>
 * </ul>
 */
public class OrderQueryService {

    private final OrderQueryStore orderQueryStore;

    public OrderQueryService(OrderQueryStore orderQueryStore) {
        this.orderQueryStore = orderQueryStore;
    }

    /**
     * 分页查询订单。
     *
     * @param query 查询条件（含分页参数）
     * @return 分页结果（无匹配时为空列表）
     * @throws ValidationException 条件非法（无过滤条件/时间区间颠倒/分页参数越界）
     */
    public PagedResult<RiskOrderView> query(OrderQuery query) {
        ValidationException.Builder errors = ValidationException.builder();

        // R10.5：至少提供一个过滤条件
        if (!query.hasAnyFilter()) {
            errors.field("filter", "至少需提供商户、事件类型或时间范围之一");
        }
        // R10.5：起始时间不得晚于结束时间
        if (query.isTimeRangeInverted()) {
            errors.field("timeRange", "起始时间不得晚于结束时间");
        }
        // R10.4：分页参数合法性
        if (query.page() < 1) {
            errors.field("page", "页码须从 1 开始");
        }
        if (query.pageSize() < 1 || query.pageSize() > OrderQuery.MAX_PAGE_SIZE) {
            errors.field("pageSize", "每页大小须在 1–" + OrderQuery.MAX_PAGE_SIZE + " 之间");
        }
        errors.throwIfAny();

        return orderQueryStore.query(query);
    }
}
