package com.riskplatform.ruleconfig.domain.eventtype;

import java.util.List;
import java.util.Optional;

/**
 * 事件类型仓储端口（R1 / risk-console-redesign R2）。
 * 由基础设施层用 MyBatis-Plus 持久化到 event_type 表。
 */
public interface EventTypeRepository {

    /** 保存新事件类型，返回带 id 的实体。 */
    EventType save(EventType eventType);

    /** 更新事件类型（状态、名称、场景、用途、分型等）。 */
    void update(EventType eventType);

    /** 按 code 查询。 */
    Optional<EventType> findByCode(String code);

    /** 按 id 查询。 */
    Optional<EventType> findById(Long id);

    /** code 是否已存在。 */
    boolean existsByCode(String code);

    /** code 是否已被「除 excludeId 外」的其它事件使用（编辑/范围内唯一校验用）。 */
    boolean existsByCodeExcludingId(String code, Long excludeId);

    /** 查询全部事件类型（无则空列表）。 */
    List<EventType> findAll();

    /** 按所属业务场景查询事件（R2.1，无则空列表）。 */
    List<EventType> findByScenarioId(Long scenarioId);

    /** 按 id 删除（R2.8）。 */
    void deleteById(Long id);
}
