package com.riskplatform.ruleconfig.domain.scenario;

import java.util.List;
import java.util.Optional;

/**
 * 场景仓储端口（R10）。由基础设施层用 MyBatis-Plus 持久化到 scenario / scenario_event 表。
 */
public interface ScenarioRepository {

    /** 保存新场景（含关联事件），返回带 id 的实体。 */
    Scenario save(Scenario scenario);

    /** 更新场景（名称/状态/关联事件）。 */
    void update(Scenario scenario);

    /** 按 code 查询。 */
    Optional<Scenario> findByCode(String code);

    /** 按 id 查询（含关联事件）。 */
    Optional<Scenario> findById(Long id);

    /** code 是否已存在。 */
    boolean existsByCode(String code);

    /** 查询全部场景（无则空列表）。 */
    List<Scenario> findAll();

    /** 按关联事件类型编码筛选场景。 */
    List<Scenario> findByEventTypeCode(String eventTypeCode);
}
