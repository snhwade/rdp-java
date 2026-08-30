package com.riskplatform.gateway.infrastructure.order;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.riskplatform.common.model.PagedResult;
import com.riskplatform.gateway.domain.BusinessOrderQuery;
import com.riskplatform.gateway.domain.BusinessOrderQueryStore;
import com.riskplatform.gateway.domain.BusinessOrderSummaryView;
import com.riskplatform.gateway.domain.ContextFieldSupport;
import com.riskplatform.gateway.domain.OrderQuery;
import com.riskplatform.gateway.domain.OrderQueryStore;
import com.riskplatform.gateway.domain.OrderStore;
import com.riskplatform.gateway.domain.RiskOrderView;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

/**
 * 订单持久化（MySQL 实现，R10）+ 订单维度聚合查询。
 */
public class MySqlOrderRepository implements OrderStore, OrderQueryStore, BusinessOrderQueryStore {

    private static final ZoneId ZONE = ZoneId.systemDefault();

    private final RiskOrderMapper mapper;

    public MySqlOrderRepository(RiskOrderMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void persistAsync(
            String eventId,
            String businessOrderId,
            String eventTypeCode,
            Map<String, Object> context,
            long eventTimeMs) {
        if (findByEventId(eventId) != null) {
            return;
        }
        RiskOrderPO po = new RiskOrderPO();
        po.setEventId(eventId);
        po.setBusinessOrderId(businessOrderId);
        po.setEventTypeCode(eventTypeCode);
        po.setMerchantId(ContextFieldSupport.extractMerchantId(context));
        po.setContext(serializeContext(context));
        po.setEventTime(toLocalDateTime(eventTimeMs));
        mapper.insert(po);
    }

    @Override
    public void updateDecisionAsync(String eventId, String finalDecision) {
        RiskOrderPO po = findByEventId(eventId);
        if (po == null) {
            return;
        }
        po.setFinalDecision(finalDecision);
        mapper.updateById(po);
    }

    @Override
    public PagedResult<RiskOrderView> query(OrderQuery query) {
        LambdaQueryWrapper<RiskOrderPO> wrapper = new LambdaQueryWrapper<>();
        if (notBlank(query.merchantId())) {
            wrapper.eq(RiskOrderPO::getMerchantId, query.merchantId());
        }
        if (notBlank(query.eventTypeCode())) {
            wrapper.eq(RiskOrderPO::getEventTypeCode, query.eventTypeCode());
        }
        if (query.startTimeMs() != null) {
            wrapper.ge(RiskOrderPO::getEventTime, toLocalDateTime(query.startTimeMs()));
        }
        if (query.endTimeMs() != null) {
            wrapper.le(RiskOrderPO::getEventTime, toLocalDateTime(query.endTimeMs()));
        }
        wrapper.orderByDesc(RiskOrderPO::getEventTime);

        Page<RiskOrderPO> page = Page.of(query.page(), query.pageSize());
        IPage<RiskOrderPO> result = mapper.selectPage(page, wrapper);

        List<RiskOrderView> views = result.getRecords().stream().map(this::toView).toList();
        return PagedResult.of(views, query.page(), query.pageSize(), result.getTotal());
    }

    @Override
    public PagedResult<BusinessOrderSummaryView> querySummaries(BusinessOrderQuery query) {
        Page<BusinessOrderSummaryRow> page = Page.of(query.page(), query.pageSize());
        LocalDateTime start = query.startTimeMs() == null ? null : toLocalDateTime(query.startTimeMs());
        LocalDateTime end = query.endTimeMs() == null ? null : toLocalDateTime(query.endTimeMs());
        IPage<BusinessOrderSummaryRow> result = mapper.pageBusinessOrders(
                page, query.businessOrderId(), query.merchantId(), query.eventTypeCode(), start, end);
        List<BusinessOrderSummaryView> views = result.getRecords().stream().map(this::toSummaryView).toList();
        return PagedResult.of(views, query.page(), query.pageSize(), result.getTotal());
    }

    @Override
    public PagedResult<RiskOrderView> listInvocations(String businessOrderId, int page, int pageSize) {
        LambdaQueryWrapper<RiskOrderPO> wrapper = new LambdaQueryWrapper<>();
        wrapper.apply("IFNULL(business_order_id, event_id) = {0}", businessOrderId);
        wrapper.orderByDesc(RiskOrderPO::getEventTime);
        Page<RiskOrderPO> p = Page.of(page, pageSize);
        IPage<RiskOrderPO> result = mapper.selectPage(p, wrapper);
        List<RiskOrderView> views = result.getRecords().stream().map(this::toView).toList();
        return PagedResult.of(views, page, pageSize, result.getTotal());
    }

    private RiskOrderPO findByEventId(String eventId) {
        return mapper.selectOne(new LambdaQueryWrapper<RiskOrderPO>()
                .eq(RiskOrderPO::getEventId, eventId)
                .last("LIMIT 1"));
    }

    private RiskOrderView toView(RiskOrderPO po) {
        long ts = po.getEventTime() == null ? 0L
                : po.getEventTime().atZone(ZONE).toInstant().toEpochMilli();
        String bizOrderId = po.getBusinessOrderId() != null ? po.getBusinessOrderId() : po.getEventId();
        return new RiskOrderView(
                po.getEventId(),
                bizOrderId,
                po.getEventTypeCode(),
                po.getMerchantId(),
                ts,
                po.getFinalDecision());
    }

    private BusinessOrderSummaryView toSummaryView(BusinessOrderSummaryRow row) {
        long lastMs = row.getLastEventTime() == null ? 0L
                : row.getLastEventTime().atZone(ZONE).toInstant().toEpochMilli();
        return new BusinessOrderSummaryView(
                row.getBusinessOrderId(),
                row.getMerchantId(),
                row.getEventTypeCode(),
                row.getInvocationCount() == null ? 0 : row.getInvocationCount().intValue(),
                lastMs,
                row.getLatestFinalDecision());
    }

    private static String serializeContext(Map<String, Object> context) {
        return context == null ? null : context.toString();
    }

    private static LocalDateTime toLocalDateTime(long epochMs) {
        return Instant.ofEpochMilli(epochMs).atZone(ZONE).toLocalDateTime();
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
