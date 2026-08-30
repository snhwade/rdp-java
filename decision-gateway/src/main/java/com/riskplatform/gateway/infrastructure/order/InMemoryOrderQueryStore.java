package com.riskplatform.gateway.infrastructure.order;

import com.riskplatform.common.model.PagedResult;
import com.riskplatform.gateway.domain.OrderQuery;
import com.riskplatform.gateway.domain.OrderQueryStore;
import com.riskplatform.gateway.domain.RiskOrderView;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 订单查询端口的内存实现（R10.4）。
 *
 * <p>当前阶段以进程内存承载已落库订单，提供按商户/事件类型/时间范围过滤与分页查询；
 * 生产实现将由基于 MyBatis-Plus 的 MySQL 分页查询替换（risk_order 表，索引
 * merchant_id/event_type_code/event_time）。本实现保证查询语义一致：过滤、按事件时间倒序、
 * 分页（每页 ≤200）、无结果返回空列表。
 *
 * <p>线程安全：以 {@link ConcurrentHashMap} 承载，同一 eventId 至多一条（R10.1）。
 */
public class InMemoryOrderQueryStore implements OrderQueryStore {

    private final Map<String, RiskOrderView> orders = new ConcurrentHashMap<>();

    /** 落库或更新一条订单（供受理链路与测试写入）。 */
    public void put(RiskOrderView order) {
        orders.put(order.eventId(), order);
    }

    @Override
    public PagedResult<RiskOrderView> query(OrderQuery query) {
        List<RiskOrderView> matched = new ArrayList<>();
        for (RiskOrderView o : orders.values()) {
            if (matches(o, query)) {
                matched.add(o);
            }
        }
        // 按事件时间倒序（最新在前），保证分页确定性
        matched.sort(Comparator.comparingLong(RiskOrderView::eventTimeMs).reversed()
                .thenComparing(RiskOrderView::eventId));

        long total = matched.size();
        int fromIndex = Math.min((query.page() - 1) * query.pageSize(), matched.size());
        int toIndex = Math.min(fromIndex + query.pageSize(), matched.size());
        List<RiskOrderView> pageData = new ArrayList<>(matched.subList(fromIndex, toIndex));

        return PagedResult.of(pageData, query.page(), query.pageSize(), total);
    }

    private boolean matches(RiskOrderView o, OrderQuery q) {
        if (notBlank(q.merchantId()) && !q.merchantId().equals(o.merchantId())) {
            return false;
        }
        if (notBlank(q.eventTypeCode()) && !q.eventTypeCode().equals(o.eventTypeCode())) {
            return false;
        }
        if (q.startTimeMs() != null && o.eventTimeMs() < q.startTimeMs()) {
            return false;
        }
        if (q.endTimeMs() != null && o.eventTimeMs() > q.endTimeMs()) {
            return false;
        }
        return true;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
