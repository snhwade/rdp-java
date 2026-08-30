package com.riskplatform.screening.application.list;

import com.riskplatform.common.error.BizException;
import com.riskplatform.screening.domain.list.ListRecord;
import com.riskplatform.screening.domain.list.ListRecordRepository;
import com.riskplatform.screening.domain.list.ListType;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 名单管理应用服务（S1：名单 CRUD + 黑白名单判定）。
 *
 * <p>提供名单记录的增删改查，以及受理链路所需的黑/白名单命中判定：
 * <ul>
 *   <li>黑名单命中：主体维度值命中启用且未过期的 BLACK 记录 → 应拦截（REJECT）；</li>
 *   <li>白名单命中：命中 WHITE 记录 → 对其 immuneRuleId 指定规则免疫（null 表示对所有规则免疫）。</li>
 * </ul>
 */
public class ListManagementService {

    private final ListRecordRepository repository;

    public ListManagementService(ListRecordRepository repository) {
        this.repository = repository;
    }

    public ListRecord create(ListType listType, String dimension, String dimensionValue,
                             String reason, Long immuneRuleId, LocalDateTime expireAt) {
        ListRecord record = ListRecord.create(listType, dimension, dimensionValue, reason, immuneRuleId, expireAt);
        return repository.save(record);
    }

    public ListRecord update(Long id, String dimensionValue, String reason,
                             Long immuneRuleId, LocalDateTime expireAt, boolean enabled) {
        ListRecord existing = repository.findById(id)
                .orElseThrow(() -> BizException.notFound("名单记录不存在: id=" + id));
        ListRecord updated = new ListRecord(existing.id(), existing.listType(), existing.dimension(),
                dimensionValue == null ? existing.dimensionValue() : dimensionValue,
                reason, immuneRuleId, expireAt, enabled);
        return repository.update(updated);
    }

    public void delete(Long id) {
        if (repository.findById(id).isEmpty()) {
            throw BizException.notFound("名单记录不存在: id=" + id);
        }
        repository.deleteById(id);
    }

    public List<ListRecord> list(ListType listType) {
        return listType == null ? repository.findAll() : repository.findByType(listType);
    }

    /** 幂等加入名单：同维度+值+类型已存在启用记录则不重复添加（供规则触发自动加名单用）。 */
    public ListRecord addIfAbsent(ListType listType, String dimension, String dimensionValue,
                                  String reason, LocalDateTime expireAt) {
        boolean exists = repository.findActiveByDimensionValue(dimension, dimensionValue).stream()
                .anyMatch(r -> r.listType() == listType);
        if (exists) {
            return null;
        }
        return create(listType, dimension, dimensionValue, reason, null, expireAt);
    }

    /** 黑名单命中判定：返回命中的黑名单记录（按维度值精确匹配，启用且未过期）。 */
    public List<ListRecord> matchBlack(String dimension, String dimensionValue) {
        return repository.findActiveByDimensionValue(dimension, dimensionValue).stream()
                .filter(r -> r.listType() == ListType.BLACK)
                .toList();
    }

    /** 关注名单命中判定：精确匹配，用于标记复核（不直接拦截）。 */
    public List<ListRecord> matchWatch(String dimension, String dimensionValue) {
        return repository.findActiveByDimensionValue(dimension, dimensionValue).stream()
                .filter(r -> r.listType() == ListType.WATCH)
                .toList();
    }

    /** 白名单命中判定：返回命中的白名单记录（用于规则免疫）。 */
    public List<ListRecord> matchWhite(String dimension, String dimensionValue) {
        return repository.findActiveByDimensionValue(dimension, dimensionValue).stream()
                .filter(r -> r.listType() == ListType.WHITE)
                .toList();
    }
}
