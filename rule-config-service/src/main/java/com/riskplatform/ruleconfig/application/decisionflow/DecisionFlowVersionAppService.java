package com.riskplatform.ruleconfig.application.decisionflow;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.riskplatform.common.error.BizException;
import com.riskplatform.common.error.ValidationException;
import com.riskplatform.ruleconfig.application.permission.UserContextProvider;
import com.riskplatform.ruleconfig.domain.decisionflow.DecisionFlow;
import com.riskplatform.ruleconfig.domain.decisionflow.DecisionFlowRepository;
import com.riskplatform.ruleconfig.domain.decisionflow.DecisionFlowVersion;
import com.riskplatform.ruleconfig.domain.decisionflow.DecisionFlowVersionRepository;
import com.riskplatform.ruleconfig.domain.decisionmatrix.DecisionMatrixRepository;
import com.riskplatform.ruleconfig.domain.decisiontable.DecisionTableRepository;
import com.riskplatform.ruleconfig.domain.decisiontree.DecisionTreeRepository;
import com.riskplatform.ruleconfig.domain.eventtype.EventTypeRepository;
import com.riskplatform.ruleconfig.domain.rulepackage.RulePackageRepository;
import com.riskplatform.ruleconfig.domain.scorecard.ScorecardRepository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 决策流版本应用服务：快照、上线/下线、对比、上线前校验（V1）、回退上一启用（R1）。
 */
public class DecisionFlowVersionAppService {

    private static final List<String> DIFF_FIELDS = List.of(
            "name", "eventTypeCode", "startNodeId", "status", "remark",
            "scenarioIds", "eventCodes", "applicableOrgId", "includeSubOrg",
            "nodes", "edges");

    private final DecisionFlowVersionRepository versionRepository;
    private final DecisionFlowRepository flowRepository;
    private final EventTypeRepository eventTypeRepository;
    private final RulePackageRepository rulePackageRepository;
    private final DecisionTableRepository decisionTableRepository;
    private final DecisionTreeRepository decisionTreeRepository;
    private final DecisionMatrixRepository decisionMatrixRepository;
    private final ScorecardRepository scorecardRepository;
    private final ObjectMapper objectMapper;
    private final UserContextProvider userContextProvider;

    public DecisionFlowVersionAppService(DecisionFlowVersionRepository versionRepository,
                                         DecisionFlowRepository flowRepository,
                                         EventTypeRepository eventTypeRepository,
                                         RulePackageRepository rulePackageRepository,
                                         DecisionTableRepository decisionTableRepository,
                                         DecisionTreeRepository decisionTreeRepository,
                                         DecisionMatrixRepository decisionMatrixRepository,
                                         ScorecardRepository scorecardRepository,
                                         ObjectMapper objectMapper,
                                         UserContextProvider userContextProvider) {
        this.versionRepository = versionRepository;
        this.flowRepository = flowRepository;
        this.eventTypeRepository = eventTypeRepository;
        this.rulePackageRepository = rulePackageRepository;
        this.decisionTableRepository = decisionTableRepository;
        this.decisionTreeRepository = decisionTreeRepository;
        this.decisionMatrixRepository = decisionMatrixRepository;
        this.scorecardRepository = scorecardRepository;
        this.objectMapper = objectMapper;
        this.userContextProvider = userContextProvider;
    }

    public DecisionFlowVersion snapshot(DecisionFlow flow) {
        if (flow == null || flow.getId() == null) {
            throw BizException.invalidState("决策流尚未持久化，无法生成版本快照");
        }
        int nextVersion = versionRepository.findMaxVersion(flow.getId()) + 1;
        String snapshotJson = writeJson(toSnapshotMap(flow));
        String createdBy = currentUsername();
        DecisionFlowVersion version =
                new DecisionFlowVersion(flow.getId(), nextVersion, snapshotJson, createdBy);
        return versionRepository.save(version);
    }

    public List<VersionSummary> listVersions(Long decisionFlowId) {
        return versionRepository.findByDecisionFlowId(decisionFlowId).stream()
                .map(v -> new VersionSummary(v.getVersion(), v.getStatus(), v.getCreatedBy(), v.getCreatedAt()))
                .toList();
    }

    public Set<Long> findOnlineFlowIds(List<Long> flowIds) {
        return versionRepository.findOnlineFlowIds(flowIds);
    }

    /**
     * 将指定版本上线（R8.6 + V1 上线前校验）。
     */
    public void onlineVersion(Long decisionFlowId, int version) {
        DecisionFlowVersion target = versionRepository.findByDecisionFlowIdAndVersion(decisionFlowId, version)
                .orElseThrow(() -> BizException.notFound(
                        "决策流版本不存在: flowId=" + decisionFlowId + ", version=" + version));
        validateBeforeOnline(target);

        Integer previousOnline = versionRepository.findOnlineVersion(decisionFlowId)
                .filter(current -> current.getVersion() != version)
                .map(DecisionFlowVersion::getVersion)
                .orElse(null);

        versionRepository.findOnlineVersion(decisionFlowId)
                .filter(current -> current.getVersion() != version)
                .ifPresent(current -> versionRepository.updateStatus(
                        decisionFlowId, current.getVersion(), DecisionFlowVersion.STATUS_OFFLINE));
        versionRepository.updateStatus(decisionFlowId, version, DecisionFlowVersion.STATUS_ONLINE);

        if (previousOnline != null) {
            flowRepository.findById(decisionFlowId).ifPresent(flow -> {
                flow.assignPrevOnlineVersion(previousOnline);
                flowRepository.update(flow);
            });
        }
    }

    /**
     * 回退到上一启用版本（R1）：将 {@code prev_online_version} 所指版本重新上线。
     */
    public void rollbackToPreviousOnline(Long decisionFlowId) {
        DecisionFlow flow = flowRepository.findById(decisionFlowId)
                .orElseThrow(() -> BizException.notFound("决策流不存在: id=" + decisionFlowId));
        Integer prev = flow.getPrevOnlineVersion();
        if (prev == null) {
            throw BizException.invalidState("无上一启用版本，至少需成功切换上线过一次才可回退");
        }
        onlineVersion(decisionFlowId, prev);
    }

    public void offlineFlow(Long decisionFlowId) {
        versionRepository.findOnlineVersion(decisionFlowId)
                .ifPresent(current -> versionRepository.updateStatus(
                        decisionFlowId, current.getVersion(), DecisionFlowVersion.STATUS_OFFLINE));
    }

    public CompareResult compare(Long decisionFlowId, int fromVersion, int toVersion) {
        DecisionFlowVersion from = versionRepository
                .findByDecisionFlowIdAndVersion(decisionFlowId, fromVersion)
                .orElseThrow(() -> BizException.notFound(
                        "决策流版本不存在: flowId=" + decisionFlowId + ", version=" + fromVersion));
        DecisionFlowVersion to = versionRepository
                .findByDecisionFlowIdAndVersion(decisionFlowId, toVersion)
                .orElseThrow(() -> BizException.notFound(
                        "决策流版本不存在: flowId=" + decisionFlowId + ", version=" + toVersion));

        Map<String, Object> fromMap = readMap(from.getSnapshotJson());
        Map<String, Object> toMap = readMap(to.getSnapshotJson());
        List<FieldDiff> diffs = diffFields(fromMap, toMap);

        return new CompareResult(
                decisionFlowId,
                new VersionSnapshot(from.getVersion(), from.getCreatedBy(), from.getCreatedAt(), fromMap),
                new VersionSnapshot(to.getVersion(), to.getCreatedBy(), to.getCreatedAt(), toMap),
                diffs);
    }

    /** V1：上线前校验绑定事件、结构、节点引用。 */
    void validateBeforeOnline(DecisionFlowVersion version) {
        Map<String, Object> snap = readMap(version.getSnapshotJson());
        DecisionFlow flow = flowFromSnapshot(snap);
        ValidationException.Builder errors = ValidationException.builder();

        try {
            flow.validate();
            flow.validateStructure();
        } catch (ValidationException ex) {
            int i = 0;
            Map<String, String> fieldMap = ex.getFields() != null ? ex.getFields() : Map.of();
            for (Map.Entry<String, String> e : fieldMap.entrySet()) {
                errors.field("structure." + e.getKey() + "." + (i++), e.getValue());
            }
        }

        String eventCode = flow.getEventTypeCode();
        if (eventCode == null || eventCode.isBlank() || !eventTypeRepository.existsByCode(eventCode)) {
            errors.field("eventTypeCode", "绑定事件不存在: " + eventCode);
        }

        if (flow.getNodes() != null) {
            for (DecisionFlow.Node n : flow.getNodes()) {
                checkNodeRef(n, errors);
            }
        }
        errors.throwIfAny();
    }

    private void checkNodeRef(DecisionFlow.Node n, ValidationException.Builder errors) {
        DecisionFlow.NodeType type = n.type();
        if (type == null) {
            return;
        }
        switch (type) {
            case RULE_PACKAGE -> requireRef(n, errors, "规则包",
                    id -> rulePackageRepository.findById(id).isPresent());
            case SUB_FLOW -> requireRef(n, errors, "子决策流",
                    id -> flowRepository.findById(id).isPresent());
            case DECISION_TABLE -> requireRef(n, errors, "决策表",
                    id -> decisionTableRepository.findById(id).isPresent());
            case SCORECARD -> requireRef(n, errors, "评分卡",
                    id -> scorecardRepository.findById(id).isPresent());
            case DECISION_TOOL -> requireDecisionTool(n, errors);
            case MODEL, LIST_CHECK, START, END, CONDITION_GATEWAY, PARALLEL_GATEWAY, CHAMPION_CHALLENGER -> {
                // MODEL/LIST_CHECK 外部或可选引用；网关无 ref
            }
        }
    }

    private void requireDecisionTool(DecisionFlow.Node n, ValidationException.Builder errors) {
        if (n.refId() == null) {
            errors.field("ref[" + n.nodeId() + "]", "决策工具节点缺少引用 id");
            return;
        }
        Long id = n.refId();
        String refType = n.refType() == null ? "" : n.refType().toUpperCase();
        boolean ok = switch (refType) {
            case "DECISION_TABLE", "TABLE" -> decisionTableRepository.findById(id).isPresent();
            case "DECISION_TREE", "TREE" -> decisionTreeRepository.findById(id).isPresent();
            case "DECISION_MATRIX", "MATRIX" -> decisionMatrixRepository.findById(id).isPresent();
            case "SCORECARD" -> scorecardRepository.findById(id).isPresent();
            default -> decisionTableRepository.findById(id).isPresent()
                    || decisionTreeRepository.findById(id).isPresent()
                    || decisionMatrixRepository.findById(id).isPresent()
                    || scorecardRepository.findById(id).isPresent();
        };
        if (!ok) {
            errors.field("ref[" + n.nodeId() + "]",
                    "决策工具引用不存在: refType=" + n.refType() + ", refId=" + id);
        }
    }

    private void requireRef(DecisionFlow.Node n, ValidationException.Builder errors,
                            String label, java.util.function.LongPredicate exists) {
        if (n.refId() == null) {
            errors.field("ref[" + n.nodeId() + "]", label + "节点缺少引用 id");
            return;
        }
        if (!exists.test(n.refId())) {
            errors.field("ref[" + n.nodeId() + "]", label + "引用不存在: id=" + n.refId());
        }
    }

    private DecisionFlow flowFromSnapshot(Map<String, Object> snap) {
        ObjectMapper lenient = objectMapper.copy()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        String name = stringVal(snap.get("name"));
        String eventTypeCode = stringVal(snap.get("eventTypeCode"));
        String startNodeId = stringVal(snap.get("startNodeId"));
        List<DecisionFlow.Node> nodes = lenient.convertValue(
                snap.get("nodes"), new TypeReference<List<DecisionFlow.Node>>() {});
        List<DecisionFlow.Edge> edges = lenient.convertValue(
                snap.get("edges"), new TypeReference<List<DecisionFlow.Edge>>() {});
        DecisionFlow flow = DecisionFlow.create(name, eventTypeCode, nodes, edges, startNodeId);
        flow.assignRemark(stringVal(snap.get("remark")));
        List<Long> scenarioIds = lenient.convertValue(snap.get("scenarioIds"), new TypeReference<List<Long>>() {});
        List<String> eventCodes = lenient.convertValue(snap.get("eventCodes"), new TypeReference<List<String>>() {});
        Long orgId = snap.get("applicableOrgId") == null ? null
                : lenient.convertValue(snap.get("applicableOrgId"), Long.class);
        boolean includeSub = Boolean.TRUE.equals(snap.get("includeSubOrg"));
        flow.assignScope(scenarioIds, eventCodes, orgId, includeSub);
        return flow;
    }

    private static String stringVal(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private List<FieldDiff> diffFields(Map<String, Object> fromMap, Map<String, Object> toMap) {
        Set<String> fields = new LinkedHashSet<>(DIFF_FIELDS);
        fields.addAll(fromMap.keySet());
        fields.addAll(toMap.keySet());
        List<FieldDiff> diffs = new ArrayList<>();
        for (String field : fields) {
            Object fromVal = fromMap.get(field);
            Object toVal = toMap.get(field);
            diffs.add(new FieldDiff(field, !Objects.equals(fromVal, toVal), fromVal, toVal));
        }
        return diffs;
    }

    private Map<String, Object> toSnapshotMap(DecisionFlow flow) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", flow.getName());
        map.put("eventTypeCode", flow.getEventTypeCode());
        map.put("startNodeId", flow.getStartNodeId());
        map.put("status", flow.getStatus());
        map.put("remark", flow.getRemark());
        map.put("scenarioIds", flow.getScenarioIds());
        map.put("eventCodes", flow.getEventCodes());
        map.put("applicableOrgId", flow.getApplicableOrgId());
        map.put("includeSubOrg", flow.isIncludeSubOrg());
        map.put("nodes", objectMapper.convertValue(flow.getNodes(), new TypeReference<List<Object>>() {}));
        map.put("edges", objectMapper.convertValue(flow.getEdges(), new TypeReference<List<Object>>() {}));
        return map;
    }

    private String currentUsername() {
        try {
            return userContextProvider.currentUser().username();
        } catch (Exception e) {
            return "anonymous";
        }
    }

    private String writeJson(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (Exception e) {
            throw new IllegalStateException("决策流版本快照序列化失败: " + e.getMessage(), e);
        }
    }

    private Map<String, Object> readMap(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() {});
        } catch (Exception e) {
            throw new IllegalStateException("决策流版本快照反序列化失败: " + e.getMessage(), e);
        }
    }

    public record VersionSummary(int version, String status, String createdBy,
                                 java.time.LocalDateTime createdAt) {
    }

    public record VersionSnapshot(int version, String createdBy,
                                  java.time.LocalDateTime createdAt, Map<String, Object> snapshot) {
    }

    public record FieldDiff(String field, boolean changed, Object from, Object to) {
    }

    public record CompareResult(Long decisionFlowId, VersionSnapshot from,
                                VersionSnapshot to, List<FieldDiff> diffs) {
    }
}
