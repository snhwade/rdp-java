package com.riskplatform.engine.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.riskplatform.engine.domain.decisionflow.DecisionFlowDef;
import com.riskplatform.engine.domain.decisionmatrix.DecisionMatrixDef;
import com.riskplatform.engine.domain.decisiontable.DecisionTableDef;
import com.riskplatform.engine.domain.decisiontree.DecisionTreeDef;
import com.riskplatform.engine.domain.scorecard.ScorecardDef;
import com.riskplatform.engine.infrastructure.decisiontool.DecisionMatrixReadPO;
import com.riskplatform.engine.infrastructure.decisiontool.DecisionTableReadPO;
import com.riskplatform.engine.infrastructure.decisiontool.DecisionToolReadMappers;
import com.riskplatform.engine.infrastructure.decisiontool.DecisionTreeReadPO;
import com.riskplatform.engine.infrastructure.decisiontool.ScorecardReadPO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 扫描决策流节点引用的决策工具 refId，从配置库批量加载可执行定义。
 */
@Component
public class DecisionToolDefLoader {

    private static final Logger log = LoggerFactory.getLogger(DecisionToolDefLoader.class);

    private final DecisionToolReadMappers.DecisionTableReadMapper tableMapper;
    private final DecisionToolReadMappers.ScorecardReadMapper scorecardMapper;
    private final DecisionToolReadMappers.DecisionTreeReadMapper treeMapper;
    private final DecisionToolReadMappers.DecisionMatrixReadMapper matrixMapper;
    private final ObjectMapper objectMapper;

    public DecisionToolDefLoader(DecisionToolReadMappers.DecisionTableReadMapper tableMapper,
                                 DecisionToolReadMappers.ScorecardReadMapper scorecardMapper,
                                 DecisionToolReadMappers.DecisionTreeReadMapper treeMapper,
                                 DecisionToolReadMappers.DecisionMatrixReadMapper matrixMapper,
                                 ObjectMapper objectMapper) {
        this.tableMapper = tableMapper;
        this.scorecardMapper = scorecardMapper;
        this.treeMapper = treeMapper;
        this.matrixMapper = matrixMapper;
        this.objectMapper = objectMapper;
    }

    public DecisionFlowDef enrich(DecisionFlowDef base) {
        if (base == null || base.nodes() == null || base.nodes().isEmpty()) {
            return base;
        }
        ToolRefIds refs = collectRefIds(base.nodes());
        Map<Long, DecisionTableDef> tables = loadTables(refs.tableIds());
        Map<Long, ScorecardDef> scorecards = loadScorecards(refs.scorecardIds());
        Map<Long, DecisionTreeDef> trees = loadTrees(refs.treeIds());
        Map<Long, DecisionMatrixDef> matrices = loadMatrices(refs.matrixIds());
        for (Long id : refs.ambiguousIds()) {
            if (!tables.containsKey(id)) {
                loadTable(id).ifPresent(def -> tables.put(id, def));
            }
            if (!scorecards.containsKey(id)) {
                loadScorecard(id).ifPresent(def -> scorecards.put(id, def));
            }
            if (!trees.containsKey(id)) {
                loadTree(id).ifPresent(def -> trees.put(id, def));
            }
            if (!matrices.containsKey(id)) {
                loadMatrix(id).ifPresent(def -> matrices.put(id, def));
            }
        }
        return new DecisionFlowDef(
                base.nodes(), base.edges(), base.startNodeId(),
                tables, scorecards, trees, matrices);
    }

    private ToolRefIds collectRefIds(List<DecisionFlowDef.Node> nodes) {
        Set<Long> tableIds = new HashSet<>();
        Set<Long> scorecardIds = new HashSet<>();
        Set<Long> treeIds = new HashSet<>();
        Set<Long> matrixIds = new HashSet<>();
        Set<Long> ambiguousIds = new HashSet<>();
        for (DecisionFlowDef.Node node : nodes) {
            if (node.refId() == null) {
                continue;
            }
            Long refId = node.refId();
            DecisionFlowDef.NodeType type = node.type();
            if (type == DecisionFlowDef.NodeType.DECISION_TABLE) {
                tableIds.add(refId);
            } else if (type == DecisionFlowDef.NodeType.SCORECARD) {
                scorecardIds.add(refId);
            } else if (type == DecisionFlowDef.NodeType.DECISION_TOOL) {
                String refType = node.refType() == null ? "" : node.refType().trim().toUpperCase();
                if (refType.contains("MATRIX")) {
                    matrixIds.add(refId);
                } else if (refType.contains("TREE")) {
                    treeIds.add(refId);
                } else if (refType.contains("TABLE")) {
                    tableIds.add(refId);
                } else if (refType.contains("SCORE")) {
                    scorecardIds.add(refId);
                } else {
                    ambiguousIds.add(refId);
                }
            }
        }
        return new ToolRefIds(tableIds, scorecardIds, treeIds, matrixIds, ambiguousIds);
    }

    private Map<Long, DecisionTableDef> loadTables(Collection<Long> ids) {
        Map<Long, DecisionTableDef> out = new HashMap<>();
        for (Long id : ids) {
            loadTable(id).ifPresent(def -> out.put(id, def));
        }
        return out;
    }

    private Map<Long, ScorecardDef> loadScorecards(Collection<Long> ids) {
        Map<Long, ScorecardDef> out = new HashMap<>();
        for (Long id : ids) {
            loadScorecard(id).ifPresent(def -> out.put(id, def));
        }
        return out;
    }

    private Map<Long, DecisionTreeDef> loadTrees(Collection<Long> ids) {
        Map<Long, DecisionTreeDef> out = new HashMap<>();
        for (Long id : ids) {
            loadTree(id).ifPresent(def -> out.put(id, def));
        }
        return out;
    }

    private Map<Long, DecisionMatrixDef> loadMatrices(Collection<Long> ids) {
        Map<Long, DecisionMatrixDef> out = new HashMap<>();
        for (Long id : ids) {
            loadMatrix(id).ifPresent(def -> out.put(id, def));
        }
        return out;
    }

    private java.util.Optional<DecisionTableDef> loadTable(Long id) {
        DecisionTableReadPO po = tableMapper.selectById(id);
        if (po == null || !isEnabled(po.getStatus())) {
            return java.util.Optional.empty();
        }
        try {
            List<DecisionTableDef.Row> rows = objectMapper.readValue(
                    po.getRowsJson(), new TypeReference<List<DecisionTableDef.Row>>() {});
            DecisionTableDef.HitPolicy policy = DecisionTableDef.HitPolicy.valueOf(po.getHitPolicy());
            return java.util.Optional.of(new DecisionTableDef(po.getId(), po.getName(), policy, rows));
        } catch (Exception e) {
            log.warn("决策表定义加载失败: id={} {}", id, e.getMessage());
            return java.util.Optional.empty();
        }
    }

    private java.util.Optional<ScorecardDef> loadScorecard(Long id) {
        ScorecardReadPO po = scorecardMapper.selectById(id);
        if (po == null || !isEnabled(po.getStatus())) {
            return java.util.Optional.empty();
        }
        try {
            List<ScorecardDef.Variable> variables = objectMapper.readValue(
                    po.getVariablesJson(), new TypeReference<List<ScorecardDef.Variable>>() {});
            List<ScorecardDef.Level> levels = objectMapper.readValue(
                    po.getLevelsJson(), new TypeReference<List<ScorecardDef.Level>>() {});
            return java.util.Optional.of(new ScorecardDef(po.getId(), po.getName(), variables, levels));
        } catch (Exception e) {
            log.warn("评分卡定义加载失败: id={} {}", id, e.getMessage());
            return java.util.Optional.empty();
        }
    }

    private java.util.Optional<DecisionTreeDef> loadTree(Long id) {
        DecisionTreeReadPO po = treeMapper.selectById(id);
        if (po == null || !isEnabled(po.getStatus())) {
            return java.util.Optional.empty();
        }
        try {
            List<DecisionTreeDef.Node> nodes = objectMapper.readValue(
                    po.getNodesJson(), new TypeReference<List<DecisionTreeDef.Node>>() {});
            return java.util.Optional.of(new DecisionTreeDef(po.getId(), po.getRootNodeId(), nodes));
        } catch (Exception e) {
            log.warn("决策树定义加载失败: id={} {}", id, e.getMessage());
            return java.util.Optional.empty();
        }
    }

    private java.util.Optional<DecisionMatrixDef> loadMatrix(Long id) {
        DecisionMatrixReadPO po = matrixMapper.selectById(id);
        if (po == null || !isEnabled(po.getStatus())) {
            return java.util.Optional.empty();
        }
        try {
            List<DecisionMatrixDef.Bin> rowBins = objectMapper.readValue(
                    po.getRowBinsJson(), new TypeReference<List<DecisionMatrixDef.Bin>>() {});
            List<DecisionMatrixDef.Bin> colBins = objectMapper.readValue(
                    po.getColBinsJson(), new TypeReference<List<DecisionMatrixDef.Bin>>() {});
            List<DecisionMatrixDef.Cell> cells = objectMapper.readValue(
                    po.getCellsJson(), new TypeReference<List<DecisionMatrixDef.Cell>>() {});
            return java.util.Optional.of(new DecisionMatrixDef(
                    po.getId(), po.getRowVar(), rowBins, po.getColVar(), colBins, cells));
        } catch (Exception e) {
            log.warn("决策矩阵定义加载失败: id={} {}", id, e.getMessage());
            return java.util.Optional.empty();
        }
    }

    private boolean isEnabled(String status) {
        return status == null || "ENABLED".equalsIgnoreCase(status);
    }

    private static final class ToolRefIds {
        private final Set<Long> tableIds;
        private final Set<Long> scorecardIds;
        private final Set<Long> treeIds;
        private final Set<Long> matrixIds;
        private final Set<Long> ambiguousIds;

        private ToolRefIds(Set<Long> tableIds, Set<Long> scorecardIds, Set<Long> treeIds,
                           Set<Long> matrixIds, Set<Long> ambiguousIds) {
            this.tableIds = tableIds;
            this.scorecardIds = scorecardIds;
            this.treeIds = treeIds;
            this.matrixIds = matrixIds;
            this.ambiguousIds = ambiguousIds;
        }

        private Set<Long> tableIds() {
            return tableIds;
        }

        private Set<Long> scorecardIds() {
            return scorecardIds;
        }

        private Set<Long> treeIds() {
            return treeIds;
        }

        private Set<Long> matrixIds() {
            return matrixIds;
        }

        private Set<Long> ambiguousIds() {
            return ambiguousIds;
        }
    }
}
