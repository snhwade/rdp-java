package com.riskplatform.ruleconfig.infrastructure.scenario;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.riskplatform.ruleconfig.domain.scenario.Scenario;
import com.riskplatform.ruleconfig.domain.scenario.ScenarioRepository;
import com.riskplatform.ruleconfig.domain.scenario.ScenarioStatus;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Scenario 仓储 MyBatis-Plus 实现（R10）。
 *
 * <p>聚合跨两张表持久化：scenario（主体）+ scenario_event（关联事件）。
 * 关联事件采用「全量替换」策略：更新时先删除旧关联再插入新关联。
 */
@Repository
public class ScenarioRepositoryImpl implements ScenarioRepository {

    private final ScenarioMapper scenarioMapper;
    private final ScenarioEventMapper eventMapper;

    public ScenarioRepositoryImpl(ScenarioMapper scenarioMapper, ScenarioEventMapper eventMapper) {
        this.scenarioMapper = scenarioMapper;
        this.eventMapper = eventMapper;
    }

    @Override
    public Scenario save(Scenario scenario) {
        ScenarioPO po = toPO(scenario);
        scenarioMapper.insert(po);
        scenario.assignId(po.getId());
        insertEvents(po.getId(), scenario.getEventTypeCodes());
        return scenario;
    }

    @Override
    public void update(Scenario scenario) {
        scenarioMapper.updateById(toPO(scenario));
        // 全量替换关联事件
        eventMapper.delete(new LambdaQueryWrapper<ScenarioEventPO>()
                .eq(ScenarioEventPO::getScenarioId, scenario.getId()));
        insertEvents(scenario.getId(), scenario.getEventTypeCodes());
    }

    @Override
    public Optional<Scenario> findByCode(String code) {
        ScenarioPO po = scenarioMapper.selectOne(new LambdaQueryWrapper<ScenarioPO>()
                .eq(ScenarioPO::getCode, code));
        return Optional.ofNullable(po).map(this::toDomain);
    }

    @Override
    public Optional<Scenario> findById(Long id) {
        return Optional.ofNullable(scenarioMapper.selectById(id)).map(this::toDomain);
    }

    @Override
    public boolean existsByCode(String code) {
        return scenarioMapper.exists(new LambdaQueryWrapper<ScenarioPO>().eq(ScenarioPO::getCode, code));
    }

    @Override
    public List<Scenario> findAll() {
        return scenarioMapper.selectList(null).stream().map(this::toDomain).toList();
    }

    @Override
    public List<Scenario> findByEventTypeCode(String eventTypeCode) {
        List<Long> scenarioIds = eventMapper.selectList(new LambdaQueryWrapper<ScenarioEventPO>()
                        .eq(ScenarioEventPO::getEventTypeCode, eventTypeCode))
                .stream().map(ScenarioEventPO::getScenarioId).distinct().toList();
        if (scenarioIds.isEmpty()) {
            return List.of();
        }
        return scenarioMapper.selectBatchIds(scenarioIds).stream().map(this::toDomain).toList();
    }

    // —— 内部辅助 ——

    private void insertEvents(Long scenarioId, List<String> codes) {
        for (String code : codes) {
            ScenarioEventPO ePo = new ScenarioEventPO();
            ePo.setScenarioId(scenarioId);
            ePo.setEventTypeCode(code);
            eventMapper.insert(ePo);
        }
    }

    private List<String> loadEvents(Long scenarioId) {
        return eventMapper.selectList(new LambdaQueryWrapper<ScenarioEventPO>()
                        .eq(ScenarioEventPO::getScenarioId, scenarioId))
                .stream().map(ScenarioEventPO::getEventTypeCode).toList();
    }

    private ScenarioPO toPO(Scenario s) {
        ScenarioPO po = new ScenarioPO();
        po.setId(s.getId());
        po.setCode(s.getCode());
        po.setName(s.getName());
        po.setStatus(s.getStatus().name());
        return po;
    }

    private Scenario toDomain(ScenarioPO po) {
        ScenarioStatus status = "DISABLED".equals(po.getStatus())
                ? ScenarioStatus.DISABLED : ScenarioStatus.ENABLED;
        List<String> events = new ArrayList<>(loadEvents(po.getId()));
        return Scenario.rehydrate(po.getId(), po.getCode(), po.getName(), status, events);
    }
}
