package com.riskplatform.ruleconfig.application.rulepackage;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.riskplatform.common.error.BizException;
import com.riskplatform.ruleconfig.application.permission.UserContextProvider;
import com.riskplatform.ruleconfig.application.rulev2.RuleV2AppService;
import com.riskplatform.ruleconfig.domain.config.ConfigChangePublisher;
import com.riskplatform.ruleconfig.domain.rulepackage.ComputeMode;
import com.riskplatform.ruleconfig.domain.rulepackage.RulePackage;
import com.riskplatform.ruleconfig.domain.rulepackage.RulePackageEnabledSnapshot;
import com.riskplatform.ruleconfig.domain.rulepackage.RulePackageEnabledSnapshotRepository;
import com.riskplatform.ruleconfig.domain.rulepackage.RulePackageRepository;
import com.riskplatform.ruleconfig.domain.rulepackage.ScoreBand;
import com.riskplatform.ruleconfig.domain.rulepackage.WarnScoreOp;
import com.riskplatform.ruleconfig.domain.rulev2.RuleV2;
import com.riskplatform.ruleconfig.domain.rulev2.RuleV2Repository;
import com.riskplatform.ruleconfig.domain.rulev2.RuleV2Status;
import com.riskplatform.ruleconfig.domain.rulev2.condition.ConditionNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 规则包启用快照（P2）：启用时落快照；支持回退到上一启用快照。
 */
@Service
public class RulePackageEnabledSnapshotAppService {

    private static final String CONFIG_TYPE = "RULE_PACKAGE";

    private final RulePackageEnabledSnapshotRepository snapshotRepository;
    private final RulePackageRepository packageRepository;
    private final RuleV2Repository ruleV2Repository;
    private final RuleV2AppService ruleV2AppService;
    private final ConfigChangePublisher configChangePublisher;
    private final ObjectMapper objectMapper;
    private final UserContextProvider userContextProvider;

    public RulePackageEnabledSnapshotAppService(
            RulePackageEnabledSnapshotRepository snapshotRepository,
            RulePackageRepository packageRepository,
            RuleV2Repository ruleV2Repository,
            RuleV2AppService ruleV2AppService,
            ConfigChangePublisher configChangePublisher,
            ObjectMapper objectMapper,
            UserContextProvider userContextProvider) {
        this.snapshotRepository = snapshotRepository;
        this.packageRepository = packageRepository;
        this.ruleV2Repository = ruleV2Repository;
        this.ruleV2AppService = ruleV2AppService;
        this.configChangePublisher = configChangePublisher;
        this.objectMapper = objectMapper;
        this.userContextProvider = userContextProvider;
    }

    /** 在规则包启用成功后调用：写入当前整包快照。 */
    public RulePackageEnabledSnapshot captureAfterEnable(Long rulePackageId) {
        RulePackage pkg = packageRepository.findById(rulePackageId)
                .orElseThrow(() -> BizException.notFound("规则包不存在: " + rulePackageId));
        List<RuleV2> rules = new ArrayList<>();
        for (var item : ruleV2Repository.findListItemsByRulePackageId(rulePackageId)) {
            ruleV2Repository.findById(item.id()).ifPresent(rules::add);
        }
        int next = snapshotRepository.findMaxVersion(rulePackageId) + 1;
        String json = writeJson(toSnapshotMap(pkg, rules));
        return snapshotRepository.save(
                new RulePackageEnabledSnapshot(rulePackageId, next, json, currentUsername()));
    }

    public List<SnapshotSummary> listSnapshots(Long rulePackageId) {
        packageRepository.findById(rulePackageId)
                .orElseThrow(() -> BizException.notFound("规则包不存在: " + rulePackageId));
        return snapshotRepository.findByRulePackageId(rulePackageId).stream()
                .map(s -> new SnapshotSummary(s.getVersion(), s.getCreatedBy(), s.getCreatedAt()))
                .toList();
    }

    /**
     * 回退到上一启用快照（版本号降序的第二条）。
     * 恢复包基础信息、分值区间、规则内容与三态，并将包保持为启用；成功后再落一条启用快照。
     */
    @Transactional
    public RulePackage rollbackToPreviousEnabled(Long rulePackageId) {
        List<RulePackageEnabledSnapshot> all = snapshotRepository.findByRulePackageId(rulePackageId);
        if (all.size() < 2) {
            throw BizException.invalidState("无上一启用快照，至少需成功启用过两次才可回退");
        }
        RulePackageEnabledSnapshot previous = all.get(1);
        RulePackage restored = applySnapshot(rulePackageId, previous);
        captureAfterEnable(rulePackageId);
        return restored;
    }

    private RulePackage applySnapshot(Long rulePackageId, RulePackageEnabledSnapshot snapshot) {
        try {
            JsonNode root = objectMapper.readTree(snapshot.getSnapshotJson());
            JsonNode pkgNode = root.path("package");
            List<ScoreBand> bands = parseScoreBands(root.path("scoreBands"));

            RulePackage pkg = packageRepository.findById(rulePackageId)
                    .orElseThrow(() -> BizException.notFound("规则包不存在: " + rulePackageId));

            ComputeMode computeMode = enumOrNull(text(pkgNode, "computeMode"), ComputeMode.class);
            WarnScoreOp warnOp = enumOrNull(text(pkgNode, "warnScoreOp"), WarnScoreOp.class);
            List<Long> scenarioIds = objectMapper.convertValue(
                    pkgNode.path("scenarioIds"), new TypeReference<List<Long>>() {});
            List<String> eventTypeCodes = objectMapper.convertValue(
                    pkgNode.path("eventTypeCodes"), new TypeReference<List<String>>() {});

            pkg.updateBasics(
                    text(pkgNode, "name"),
                    computeMode,
                    text(pkgNode, "riskTypeCode"),
                    longOrNull(pkgNode, "ownerOrgId"),
                    longOrNull(pkgNode, "applicableOrgId"),
                    pkgNode.path("includeSubOrg").asBoolean(false));
            pkg.checkNameUnique(packageRepository::existsByTriggerModeAndName);
            pkg.replaceScenarios(scenarioIds == null ? List.of() : scenarioIds);
            pkg.replaceEvents(eventTypeCodes == null ? List.of() : eventTypeCodes);
            pkg.configureWarnScore(
                    pkgNode.path("warnScoreEnabled").asBoolean(false),
                    warnOp,
                    decimalOrNull(pkgNode, "warnScoreThreshold"));
            pkg.replaceScoreBands(bands);
            pkg.enable();
            pkg.bumpVersion();
            packageRepository.update(pkg);

            if (root.path("rules").isArray()) {
                for (JsonNode rn : root.path("rules")) {
                    Long ruleId = longOrNull(rn, "id");
                    if (ruleId == null || ruleV2Repository.findById(ruleId).isEmpty()) {
                        continue;
                    }
                    JsonNode conditionNode = rn.get("condition");
                    if (conditionNode != null && !conditionNode.isNull()) {
                        ConditionNode condition = objectMapper.treeToValue(conditionNode, ConditionNode.class);
                        if (condition != null) {
                            ruleV2AppService.update(ruleId, new RuleV2AppService.UpdateCommand(
                                    text(rn, "name"),
                                    text(rn, "eventTypeCode"),
                                    text(rn, "riskLevelCode"),
                                    text(rn, "riskTypeCode"),
                                    decimalOrNull(rn, "baseScore"),
                                    condition,
                                    rn.path("priority").asInt(0),
                                    rn.path("shortCircuited").asBoolean(false),
                                    longOrNull(rn, "applicableOrgId"),
                                    rn.path("includeSubOrg").asBoolean(false),
                                    text(rn, "remark"),
                                    null));
                        }
                    }
                    String status = text(rn, "status");
                    if (status != null && !status.isBlank()) {
                        RuleV2Status st = normalizeStatus(status);
                        if (st != null) {
                            ruleV2AppService.changeStatus(ruleId, st);
                        }
                    }
                }
            }

            configChangePublisher.publishChange(CONFIG_TYPE, String.valueOf(rulePackageId));
            return packageRepository.findById(rulePackageId).orElse(pkg);
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            throw BizException.invalidState("启用快照恢复失败: " + ex.getMessage());
        }
    }

    private Map<String, Object> toSnapshotMap(RulePackage pkg, List<RuleV2> rules) {
        Map<String, Object> root = new LinkedHashMap<>();
        Map<String, Object> packageMap = new LinkedHashMap<>();
        packageMap.put("id", pkg.getId());
        packageMap.put("code", pkg.getCode());
        packageMap.put("name", pkg.getName());
        packageMap.put("triggerMode", pkg.getTriggerMode() == null ? null : pkg.getTriggerMode().name());
        packageMap.put("computeMode", pkg.getComputeMode() == null ? null : pkg.getComputeMode().name());
        packageMap.put("riskTypeCode", pkg.getRiskTypeCode());
        packageMap.put("ownerOrgId", pkg.getOwnerOrgId());
        packageMap.put("applicableOrgId", pkg.getApplicableOrgId());
        packageMap.put("includeSubOrg", pkg.isIncludeSubOrg());
        packageMap.put("status", pkg.getStatus() == null ? null : pkg.getStatus().name());
        packageMap.put("warnScoreEnabled", pkg.isWarnScoreEnabled());
        packageMap.put("warnScoreOp", pkg.getWarnScoreOp() == null ? null : pkg.getWarnScoreOp().name());
        packageMap.put("warnScoreThreshold", pkg.getWarnScoreThreshold());
        packageMap.put("scenarioIds", pkg.getScenarioIds());
        packageMap.put("eventTypeCodes", pkg.getEventTypeCodes());
        root.put("package", packageMap);

        List<Map<String, Object>> bands = new ArrayList<>();
        for (ScoreBand b : pkg.getScoreBands()) {
            Map<String, Object> bm = new LinkedHashMap<>();
            bm.put("lower", b.getLower());
            bm.put("upper", b.getUpper());
            bm.put("lowerInclusive", b.isLowerInclusive());
            bm.put("upperInclusive", b.isUpperInclusive());
            bm.put("riskLevelCode", b.getRiskLevelCode());
            bm.put("orderNo", b.getOrderNo());
            bands.add(bm);
        }
        root.put("scoreBands", bands);

        List<Map<String, Object>> ruleMaps = new ArrayList<>();
        for (RuleV2 r : rules) {
            Map<String, Object> rm = new LinkedHashMap<>();
            rm.put("id", r.getId());
            rm.put("code", r.getCode());
            rm.put("name", r.getName());
            rm.put("status", r.getStatus() == null ? null : r.getStatus().name());
            rm.put("eventTypeCode", r.getEventTypeCode());
            rm.put("riskLevelCode", r.getRiskLevelCode());
            rm.put("riskTypeCode", r.getRiskTypeCode());
            rm.put("baseScore", r.getBaseScore());
            rm.put("priority", r.getPriority());
            rm.put("shortCircuited", r.isShortCircuited());
            rm.put("applicableOrgId", r.getApplicableOrgId());
            rm.put("includeSubOrg", r.isIncludeSubOrg());
            rm.put("remark", r.getRemark());
            rm.put("condition", r.getCondition());
            ruleMaps.add(rm);
        }
        root.put("rules", ruleMaps);
        return root;
    }

    private List<ScoreBand> parseScoreBands(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<ScoreBand> bands = new ArrayList<>();
        int i = 0;
        for (JsonNode n : node) {
            int orderNo = n.path("orderNo").isMissingNode() ? i : n.path("orderNo").asInt(i);
            bands.add(ScoreBand.of(
                    decimalOrNull(n, "lower"),
                    decimalOrNull(n, "upper"),
                    !n.has("lowerInclusive") || n.path("lowerInclusive").asBoolean(true),
                    n.path("upperInclusive").asBoolean(false),
                    text(n, "riskLevelCode"),
                    orderNo));
            i++;
        }
        return bands;
    }

    private static RuleV2Status normalizeStatus(String status) {
        return switch (status.trim().toUpperCase()) {
            case "ONLINE", "ENABLED" -> RuleV2Status.ONLINE;
            case "TRIAL_RUN" -> RuleV2Status.TRIAL_RUN;
            case "OFFLINE", "DISABLED" -> RuleV2Status.OFFLINE;
            default -> null;
        };
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw BizException.invalidState("启用快照序列化失败: " + e.getMessage());
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

    private static BigDecimal decimalOrNull(JsonNode n, String field) {
        JsonNode v = n.get(field);
        if (v == null || v.isNull() || v.isMissingNode()) {
            return null;
        }
        if (v.isNumber()) {
            return v.decimalValue();
        }
        String s = v.asText();
        if (s == null || s.isBlank()) {
            return null;
        }
        return new BigDecimal(s);
    }

    private static <E extends Enum<E>> E enumOrNull(String name, Class<E> type) {
        if (name == null || name.isBlank()) {
            return null;
        }
        return Enum.valueOf(type, name);
    }

    public record SnapshotSummary(int version, String createdBy, Instant createdAt) {
    }
}
