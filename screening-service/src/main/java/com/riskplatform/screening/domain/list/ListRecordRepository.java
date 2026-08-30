package com.riskplatform.screening.domain.list;

import java.util.List;
import java.util.Optional;

/**
 * 名单记录仓储端口（S1）。
 */
public interface ListRecordRepository {

    ListRecord save(ListRecord record);

    ListRecord update(ListRecord record);

    boolean deleteById(Long id);

    Optional<ListRecord> findById(Long id);

    /** 列出指定类型的全部记录（管理页/判定加载）。 */
    List<ListRecord> findByType(ListType listType);

    /** 列出全部记录。 */
    List<ListRecord> findAll();

    /**
     * 按维度+维度值精确查找启用且未过期的记录（黑白名单判定用）。
     * 用于受理链路快速判断主体是否命中名单。
     */
    List<ListRecord> findActiveByDimensionValue(String dimension, String dimensionValue);
}
