package com.riskplatform.ruleconfig.application.indicator;

import com.riskplatform.common.error.BizException;
import com.riskplatform.ruleconfig.domain.eventtype.EventTypeRepository;
import com.riskplatform.ruleconfig.domain.indicator.IndicatorDefinitionRepository;
import com.riskplatform.ruleconfig.domain.indicator.IndicatorGroup;
import com.riskplatform.ruleconfig.domain.indicator.IndicatorGroupRepository;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class IndicatorGroupAppService {

    private final IndicatorGroupRepository groupRepository;
    private final IndicatorDefinitionRepository indicatorRepository;
    private final EventTypeRepository eventTypeRepository;

    public IndicatorGroupAppService(IndicatorGroupRepository groupRepository,
                                    IndicatorDefinitionRepository indicatorRepository,
                                    EventTypeRepository eventTypeRepository) {
        this.groupRepository = groupRepository;
        this.indicatorRepository = indicatorRepository;
        this.eventTypeRepository = eventTypeRepository;
    }

    public IndicatorGroup create(String name, String orgName, List<String> eventTypeCodes, String description) {
        validateEventTypeCodes(eventTypeCodes);
        if (groupRepository.existsByName(name)) {
            throw BizException.duplicate("指标分组名称已存在: " + name);
        }
        IndicatorGroup group = IndicatorGroup.create(name, orgName, eventTypeCodes, description);
        return groupRepository.save(group);
    }

    public List<IndicatorGroupCard> listCards() {
        return groupRepository.findAll().stream()
                .map(g -> new IndicatorGroupCard(
                        g.getId(),
                        g.getName(),
                        g.getOrgName(),
                        g.getEventTypeCodes(),
                        groupRepository.countIndicators(g.getId(), "ONLINE"),
                        groupRepository.countIndicators(g.getId(), "OFFLINE")))
                .toList();
    }

    public IndicatorGroupCard ungroupedCard() {
        long online = groupRepository.countUngroupedIndicators("ONLINE");
        long offline = groupRepository.countUngroupedIndicators("OFFLINE");
        if (online + offline == 0) {
            return null;
        }
        return new IndicatorGroupCard(null, "未分组", "总部", List.of(), online, offline);
    }

    public IndicatorGroup get(Long id) {
        return groupRepository.findById(id)
                .orElseThrow(() -> BizException.notFound("指标分组不存在: id=" + id));
    }

    public IndicatorGroup update(Long id, String name, String orgName,
                                 List<String> eventTypeCodes, String description) {
        validateEventTypeCodes(eventTypeCodes);
        IndicatorGroup group = get(id);
        if (groupRepository.existsByNameExceptId(name, id)) {
            throw BizException.duplicate("指标分组名称已存在: " + name);
        }
        group.update(name, orgName, eventTypeCodes, description);
        return groupRepository.update(group);
    }

    public void delete(Long id) {
        if (groupRepository.countIndicatorsTotal(id) > 0) {
            throw new BizException(com.riskplatform.common.error.CommonErrorCode.INVALID_FIELD,
                    "分组下仍有指标，无法删除");
        }
        if (!groupRepository.deleteById(id)) {
            throw BizException.notFound("指标分组不存在: id=" + id);
        }
    }

    private void validateEventTypeCodes(List<String> eventTypeCodes) {
        if (eventTypeCodes == null || eventTypeCodes.isEmpty()) {
            return;
        }
        Set<String> seen = new HashSet<>();
        for (String code : eventTypeCodes) {
            if (code == null || code.isBlank() || !seen.add(code)) {
                continue;
            }
            eventTypeRepository.findByCode(code)
                    .orElseThrow(() -> BizException.notFound("事件类型不存在: " + code));
        }
    }

    public record IndicatorGroupCard(
            Long id,
            String name,
            String orgName,
            List<String> eventTypeCodes,
            long onlineCount,
            long offlineCount) {
    }
}
