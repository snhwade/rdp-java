package com.riskplatform.ruleconfig.application.agent;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.riskplatform.common.error.BizException;
import com.riskplatform.common.error.CommonErrorCode;
import com.riskplatform.ruleconfig.application.permission.UserContextProvider;
import com.riskplatform.ruleconfig.infrastructure.agent.AgentStrategyAdoptionAuditMapper;
import com.riskplatform.ruleconfig.infrastructure.agent.AgentStrategyAdoptionAuditPO;
import com.riskplatform.ruleconfig.infrastructure.agent.AgentStrategyMapper;
import com.riskplatform.ruleconfig.infrastructure.agent.AgentStrategyPO;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class AgentStrategyAppService {

    private static final Pattern CODE_PATTERN = Pattern.compile("^[A-Za-z0-9_]{1,64}$");
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};
    private static final Set<String> ADOPTION_MODES = Set.of("SHADOW", "ADVISORY", "STRICT", "OVERRIDE");

    private final AgentStrategyMapper mapper;
    private final AgentStrategyAdoptionAuditMapper auditMapper;
    private final ObjectMapper objectMapper;
    private final UserContextProvider userContextProvider;

    public AgentStrategyAppService(AgentStrategyMapper mapper,
                                   AgentStrategyAdoptionAuditMapper auditMapper,
                                   ObjectMapper objectMapper,
                                   UserContextProvider userContextProvider) {
        this.mapper = mapper;
        this.auditMapper = auditMapper;
        this.objectMapper = objectMapper;
        this.userContextProvider = userContextProvider;
    }

    public List<AgentStrategyView> list() {
        return mapper.selectList(null).stream().map(this::toView).toList();
    }

    public AgentStrategyView get(long id) {
        return toView(require(id));
    }

    public AgentStrategyView resolve(String eventTypeCode) {
        List<AgentStrategyPO> all = mapper.selectList(null);
        AgentStrategyPO fallback = null;
        for (AgentStrategyPO po : all) {
            if (!"ENABLED".equals(po.getStatus())) {
                continue;
            }
            List<String> codes = parseEventCodes(po.getEventTypeCodes());
            if (codes.contains("*")) {
                fallback = po;
                continue;
            }
            if (eventTypeCode != null && codes.contains(eventTypeCode)) {
                return toView(po);
            }
        }
        if (fallback != null) {
            return toView(fallback);
        }
        throw new BizException(CommonErrorCode.NOT_FOUND, "未配置 Agent 策略");
    }

    public AgentStrategyView create(String code, String name, List<String> eventTypeCodes, String configJson,
                                    String description, String adoptionMode) {
        validateCode(code);
        validateName(name);
        String mode = normalizeAdoptionMode(adoptionMode);
        AgentStrategyPO po = new AgentStrategyPO();
        po.setCode(code.trim());
        po.setName(name.trim());
        po.setDescription(normalizeDescription(description));
        po.setEventTypeCodes(writeEventCodes(eventTypeCodes));
        po.setConfigJson(configJson == null ? "{}" : configJson);
        po.setStatus("ENABLED");
        po.setAdoptionMode(mode);
        mapper.insert(po);
        recordAdoptionChange(po, null, mode);
        return toView(po);
    }

    public AgentStrategyView update(long id, String name, List<String> eventTypeCodes, String configJson,
                                    Boolean enabled, String description, String adoptionMode) {
        AgentStrategyPO po = require(id);
        validateName(name);
        String previousMode = normalizeAdoptionMode(po.getAdoptionMode());
        po.setName(name.trim());
        po.setDescription(normalizeDescription(description));
        po.setEventTypeCodes(writeEventCodes(eventTypeCodes));
        if (configJson != null) {
            po.setConfigJson(configJson);
        }
        if (enabled != null) {
            po.setStatus(enabled ? "ENABLED" : "DISABLED");
        }
        if (adoptionMode != null) {
            String mode = normalizeAdoptionMode(adoptionMode);
            po.setAdoptionMode(mode);
            if (!mode.equals(previousMode)) {
                recordAdoptionChange(po, previousMode, mode);
            }
        }
        mapper.updateById(po);
        return toView(po);
    }

    public List<AdoptionAuditView> listAdoptionAudits(long strategyId, int limit) {
        require(strategyId);
        int lim = limit <= 0 ? 20 : Math.min(limit, 100);
        return auditMapper.selectList(new LambdaQueryWrapper<AgentStrategyAdoptionAuditPO>()
                        .eq(AgentStrategyAdoptionAuditPO::getStrategyId, strategyId)
                        .orderByDesc(AgentStrategyAdoptionAuditPO::getCreatedAt)
                        .last("LIMIT " + lim))
                .stream()
                .map(a -> new AdoptionAuditView(
                        a.getId(), a.getStrategyId(), a.getStrategyCode(),
                        a.getFromMode(), a.getToMode(), a.getChangedBy(),
                        a.getCreatedAt() == null ? null : a.getCreatedAt().toString()))
                .toList();
    }

    private void recordAdoptionChange(AgentStrategyPO po, String fromMode, String toMode) {
        AgentStrategyAdoptionAuditPO audit = new AgentStrategyAdoptionAuditPO();
        audit.setStrategyId(po.getId());
        audit.setStrategyCode(po.getCode());
        audit.setFromMode(fromMode);
        audit.setToMode(toMode);
        audit.setChangedBy(currentUsername());
        auditMapper.insert(audit);
    }

    private String currentUsername() {
        try {
            return userContextProvider.currentUser().username();
        } catch (Exception ex) {
            return "system";
        }
    }

    private AgentStrategyPO require(long id) {
        AgentStrategyPO po = mapper.selectById(id);
        if (po == null) {
            throw new BizException(CommonErrorCode.NOT_FOUND, "Agent 策略不存在");
        }
        return po;
    }

    private void validateCode(String code) {
        if (code == null || !CODE_PATTERN.matcher(code.trim()).matches()) {
            throw new BizException(CommonErrorCode.INVALID_FIELD, "策略编码格式非法", Map.of("code", "1-64位字母数字下划线"));
        }
    }

    private void validateName(String name) {
        if (name == null || name.isBlank() || name.length() > 128) {
            throw new BizException(CommonErrorCode.INVALID_FIELD, "策略名称无效", Map.of("name", "必填且不超过128字符"));
        }
    }

    private String normalizeAdoptionMode(String raw) {
        if (raw == null || raw.isBlank()) {
            return "SHADOW";
        }
        String mode = raw.trim().toUpperCase(Locale.ROOT);
        if (!ADOPTION_MODES.contains(mode)) {
            throw new BizException(CommonErrorCode.INVALID_FIELD, "采纳模式非法",
                    Map.of("adoptionMode", "须为 SHADOW/ADVISORY/STRICT/OVERRIDE"));
        }
        return mode;
    }

    private String normalizeDescription(String description) {
        if (description == null) {
            return null;
        }
        String trimmed = description.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() > 512) {
            throw new BizException(CommonErrorCode.INVALID_FIELD, "描述过长", Map.of("description", "不超过512字符"));
        }
        return trimmed;
    }

    private List<String> parseEventCodes(String json) {
        if (json == null || json.isBlank()) {
            return List.of("*");
        }
        try {
            List<String> list = objectMapper.readValue(json, STRING_LIST);
            return list == null ? List.of("*") : list;
        } catch (Exception e) {
            return List.of("*");
        }
    }

    private String writeEventCodes(List<String> codes) {
        List<String> normalized = codes == null || codes.isEmpty() ? List.of("*") : codes;
        try {
            return objectMapper.writeValueAsString(normalized);
        } catch (Exception e) {
            return "[\"*\"]";
        }
    }

    private AgentStrategyView toView(AgentStrategyPO po) {
        return new AgentStrategyView(
                po.getId(),
                po.getCode(),
                po.getName(),
                po.getDescription(),
                parseEventCodes(po.getEventTypeCodes()),
                po.getConfigJson(),
                po.getStatus(),
                normalizeAdoptionMode(po.getAdoptionMode()));
    }

    public record AgentStrategyView(
            Long id,
            String code,
            String name,
            String description,
            List<String> eventTypeCodes,
            String configJson,
            String status,
            String adoptionMode) {
    }

    public record AdoptionAuditView(
            Long id,
            Long strategyId,
            String strategyCode,
            String fromMode,
            String toMode,
            String changedBy,
            String createdAt) {
    }
}
