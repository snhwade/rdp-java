package com.riskplatform.ruleconfig.application.scenario;

import com.riskplatform.common.error.BizException;
import com.riskplatform.ruleconfig.domain.scenario.Scenario;
import com.riskplatform.ruleconfig.domain.scenario.ScenarioRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 场景应用服务（R10.1/R10.4）。
 *
 * <p>负责创建（code 唯一性校验）、更新（名称/关联事件）、启用/禁用、列表/按场景筛选事件的编排。
 * 领域不变式（name/code 校验、事件去重）在 {@link Scenario} 内完成。
 *
 * <p>通过 {@code @Service} 组件扫描自注册，避免改动共享装配类。
 */
@Service
public class ScenarioAppService {

    private final ScenarioRepository repository;

    public ScenarioAppService(ScenarioRepository repository) {
        this.repository = repository;
    }

    /** 创建场景（R10.1：名称/编码/状态 + 关联多个事件）。 */
    public Scenario create(String code, String name, List<String> eventTypeCodes) {
        Scenario scenario = Scenario.create(code, name, eventTypeCodes); // 校验 name/code（R10.1）
        if (repository.existsByCode(code)) {
            throw BizException.duplicate("场景 code 已存在: " + code); // R10.1 唯一
        }
        return repository.save(scenario);
    }

    /** 更新场景名称与关联事件（R10.1）。 */
    public Scenario update(Long id, String name, List<String> eventTypeCodes) {
        Scenario scenario = repository.findById(id)
                .orElseThrow(() -> BizException.notFound("场景不存在: " + id));
        scenario.rename(name);
        scenario.replaceEvents(eventTypeCodes);
        repository.update(scenario);
        return scenario;
    }

    /** 设置启用/禁用状态。 */
    public Scenario setStatus(Long id, boolean enabled) {
        Scenario scenario = repository.findById(id)
                .orElseThrow(() -> BizException.notFound("场景不存在: " + id));
        if (enabled) {
            scenario.enable();
        } else {
            scenario.disable();
        }
        repository.update(scenario);
        return scenario;
    }

    /** 替换场景关联事件（R10.1）。 */
    public Scenario replaceEvents(Long id, List<String> eventTypeCodes) {
        Scenario scenario = repository.findById(id)
                .orElseThrow(() -> BizException.notFound("场景不存在: " + id));
        scenario.replaceEvents(eventTypeCodes);
        repository.update(scenario);
        return scenario;
    }

    /** 按 id 查询（含关联事件）。 */
    public Scenario get(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> BizException.notFound("场景不存在: " + id));
    }

    /** 列表查询。 */
    public List<Scenario> list() {
        return repository.findAll();
    }

    /** 按场景查询其关联事件编码（R10.4）。 */
    public List<String> listEvents(Long id) {
        return get(id).getEventTypeCodes();
    }
}
