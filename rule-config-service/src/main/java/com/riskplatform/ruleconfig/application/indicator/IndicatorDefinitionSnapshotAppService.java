package com.riskplatform.ruleconfig.application.indicator;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.riskplatform.common.error.BizException;
import com.riskplatform.ruleconfig.application.permission.UserContextProvider;
import com.riskplatform.ruleconfig.domain.config.ConfigChangePublisher;
import com.riskplatform.ruleconfig.domain.indicator.IndicatorDefinition;
import com.riskplatform.ruleconfig.domain.indicator.IndicatorDefinitionRepository;
import com.riskplatform.ruleconfig.domain.indicator.IndicatorDefinitionSnapshot;
import com.riskplatform.ruleconfig.domain.indicator.IndicatorDefinitionSnapshotRepository;
import com.riskplatform.ruleconfig.domain.indicator.SliceGranularity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 指标定义快照（IV1）：更新前落快照；支持回退到上一版定义。
 */
@Service
public class IndicatorDefinitionSnapshotAppService {

    private final IndicatorDefinitionSnapshotRepository snapshotRepository;
    private final IndicatorDefinitionRepository definitionRepository;
    private final ConfigChangePublisher configChangePublisher;
    private final ObjectMapper objectMapper;
    private final UserContextProvider userContextProvider;

    public IndicatorDefinitionSnapshotAppService(
            IndicatorDefinitionSnapshotRepository snapshotRepository,
            IndicatorDefinitionRepository definitionRepository,
            ConfigChangePublisher configChangePublisher,
            ObjectMapper objectMapper,
            UserContextProvider userContextProvider) {
        this.snapshotRepository = snapshotRepository;
        this.definitionRepository = definitionRepository;
        this.configChangePublisher = configChangePublisher;
        this.objectMapper = objectMapper;
        this.userContextProvider = userContextProvider;
    }

    /** 在更新前调用：写入当前定义快照。 */
    public IndicatorDefinitionSnapshot captureBeforeUpdate(IndicatorDefinition def) {
        int next = snapshotRepository.findMaxVersion(def.getId()) + 1;
        String json = writeJson(toSnapshotMap(def));
        return snapshotRepository.save(
                new IndicatorDefinitionSnapshot(def.getId(), next, json, currentUsername()));
    }

    public List<SnapshotSummary> listSnapshots(Long indicatorDefinitionId) {
        definitionRepository.findById(indicatorDefinitionId)
                .orElseThrow(() -> BizException.notFound("指标定义不存在: id=" + indicatorDefinitionId));
        return snapshotRepository.findByIndicatorDefinitionId(indicatorDefinitionId).stream()
                .map(s -> new SnapshotSummary(s.getVersion(), s.getCreatedBy(), s.getCreatedAt()))
                .toList();
    }

    /** 回退到上一版定义快照（版本号降序的第二条）。 */
    @Transactional
    public IndicatorDefinition rollbackToPreviousDefinition(Long indicatorDefinitionId) {
        List<IndicatorDefinitionSnapshot> all =
                snapshotRepository.findByIndicatorDefinitionId(indicatorDefinitionId);
        if (all.size() < 2) {
            throw BizException.invalidState("无上一版定义快照，至少需更新过两次才可回退");
        }
        IndicatorDefinitionSnapshot previous = all.get(1);
        IndicatorDefinition restored = applySnapshot(indicatorDefinitionId, previous);
        captureBeforeUpdate(restored);
        return restored;
    }

    private IndicatorDefinition applySnapshot(Long indicatorDefinitionId, IndicatorDefinitionSnapshot snapshot) {
        try {
            IndicatorDefinition def = definitionRepository.findById(indicatorDefinitionId)
                    .orElseThrow(() -> BizException.notFound("指标定义不存在: id=" + indicatorDefinitionId));
            JsonNode root = objectMapper.readTree(snapshot.getSnapshotJson());
            List<String> eventTypeCodes = objectMapper.convertValue(
                    root.path("eventTypeCodes"), new TypeReference<List<String>>() {});
            List<String> dimensions = objectMapper.convertValue(
                    root.path("dimensions"), new TypeReference<List<String>>() {});
            Map<String, Object> templateConfig = objectMapper.convertValue(
                    root.path("templateConfig"), new TypeReference<Map<String, Object>>() {});

            def.update(
                    longOrNull(root, "groupId"),
                    text(root, "name"),
                    text(root, "description"),
                    eventTypeCodes == null ? List.of() : eventTypeCodes,
                    dimensions == null ? List.of() : dimensions,
                    root.path("windowDays").asInt(def.getWindowDays()),
                    SliceGranularity.valueOf(text(root, "sliceGranularity")),
                    text(root, "accScript"),
                    text(root, "defaultValueStrategy"),
                    text(root, "templateType"),
                    templateConfig);
            IndicatorDefinition saved = definitionRepository.update(def);
            configChangePublisher.publishChange("INDICATOR", def.getRefName());
            return saved;
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            throw BizException.invalidState("定义快照恢复失败: " + ex.getMessage());
        }
    }

    private Map<String, Object> toSnapshotMap(IndicatorDefinition d) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", d.getId());
        m.put("groupId", d.getGroupId());
        m.put("refName", d.getRefName());
        m.put("name", d.getName());
        m.put("description", d.getDescription());
        m.put("eventTypeCodes", d.getEventTypeCodes());
        m.put("dimensions", d.getDimensions());
        m.put("windowDays", d.getWindowDays());
        m.put("sliceGranularity", d.getSliceGranularity().name());
        m.put("accScript", d.getAccScript());
        m.put("defaultValueStrategy", d.getDefaultValueStrategy());
        m.put("status", d.getStatus());
        m.put("templateType", d.getTemplateType());
        m.put("templateConfig", d.getTemplateConfig());
        return m;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw BizException.invalidState("定义快照序列化失败: " + e.getMessage());
        }
    }

    private String currentUsername() {
        try {
            var user = userContextProvider.currentUser();
            return user == null || user.username() == null || user.username().isBlank()
                    ? "anonymous" : user.username();
        } catch (Exception e) {
            return "anonymous";
        }
    }

    private static String text(JsonNode n, String field) {
        JsonNode v = n.get(field);
        if (v == null || v.isNull()) {
            return null;
        }
        String s = v.asText();
        return s == null || s.isBlank() || "null".equals(s) ? null : s;
    }

    private static Long longOrNull(JsonNode n, String field) {
        JsonNode v = n.get(field);
        if (v == null || v.isNull() || !v.isNumber()) {
            return null;
        }
        return v.asLong();
    }

    public record SnapshotSummary(int version, String createdBy, Instant createdAt) {
    }
}
