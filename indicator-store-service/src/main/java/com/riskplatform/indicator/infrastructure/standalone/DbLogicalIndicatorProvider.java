package com.riskplatform.indicator.infrastructure.standalone;

import com.riskplatform.indicator.application.logical.LogicalIndicatorCatalog;
import com.riskplatform.indicator.application.logical.LogicalIndicatorDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

@Component
@ConditionalOnProperty(name = "rdp.integration.mode", havingValue = "standalone", matchIfMissing = true)
public class DbLogicalIndicatorProvider implements LogicalIndicatorCatalog {

    private static final Logger log = LoggerFactory.getLogger(DbLogicalIndicatorProvider.class);

    private final LogicalIndicatorReadMapper indicatorMapper;
    private final LogicalIndicatorMemberReadMapper memberMapper;
    private final AtomicReference<List<LogicalIndicatorDefinition>> snapshot = new AtomicReference<>(List.of());

    public DbLogicalIndicatorProvider(LogicalIndicatorReadMapper indicatorMapper,
                                      LogicalIndicatorMemberReadMapper memberMapper) {
        this.indicatorMapper = indicatorMapper;
        this.memberMapper = memberMapper;
    }

    @Override
    public List<LogicalIndicatorDefinition> current() {
        return snapshot.get();
    }

    @Override
    public Optional<LogicalIndicatorDefinition> findOnline(String refName) {
        return snapshot.get().stream()
                .filter(d -> d.refName().equals(refName))
                .findFirst();
    }

    @Override
    public void refresh() {
        try {
            List<LogicalIndicatorRow> rows = indicatorMapper.selectOnline();
            List<LogicalIndicatorDefinition> defs = new ArrayList<>();
            for (LogicalIndicatorRow row : rows) {
                List<String> members = memberMapper.selectMemberRefs(row.id()).stream()
                        .map(LogicalIndicatorMemberRow::memberRefName)
                        .toList();
                defs.add(new LogicalIndicatorDefinition(
                        row.refName(),
                        row.combineMode() == null ? "SUM" : row.combineMode(),
                        row.combineExpression(),
                        members));
            }
            snapshot.set(defs);
            log.info("逻辑指标已从 DB 刷新，共 {} 个: {}", defs.size(),
                    defs.stream().map(LogicalIndicatorDefinition::refName).toList());
        } catch (Exception ex) {
            log.warn("从 DB 拉取逻辑指标失败，保留上次快照（{} 个）。原因: {}",
                    snapshot.get().size(), ex.getMessage());
        }
    }
}
