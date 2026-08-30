package com.riskplatform.gateway.infrastructure.standalone;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.riskplatform.gateway.domain.ListGateway;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
@ConditionalOnProperty(name = "rdp.integration.mode", havingValue = "standalone", matchIfMissing = true)
public class DbListGateway implements ListGateway {

    private static final String[] DIMENSION_KEYS = {
            "merchantId", "idNo", "accountNo",
            "subjectName", "payerName", "counterpartyName", "name"
    };

    private final StandaloneListRecordMapper mapper;

    public DbListGateway(StandaloneListRecordMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public ListCheckSummary checkContext(java.util.Map<String, Object> context) {
        if (context == null || context.isEmpty()) {
            return ListCheckSummary.empty();
        }
        boolean blackHit = false;
        boolean watchHit = false;
        boolean whiteHit = false;
        boolean whiteImmuneAll = false;
        List<Long> immuneRuleIds = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        LocalDateTime now = LocalDateTime.now();

        for (String key : DIMENSION_KEYS) {
            Object raw = context.get(key);
            if (raw == null || String.valueOf(raw).isBlank()) {
                continue;
            }
            String value = String.valueOf(raw).trim();
            String sig = key + "\0" + value;
            if (!seen.add(sig)) {
                continue;
            }
            List<StandaloneListRecordPO> rows = mapper.selectList(new LambdaQueryWrapper<StandaloneListRecordPO>()
                    .eq(StandaloneListRecordPO::getDimension, key)
                    .eq(StandaloneListRecordPO::getEnabled, 1));
            for (StandaloneListRecordPO row : rows) {
                if (!isActive(row, now)) {
                    continue;
                }
                String stored = row.getDimensionValue();
                if (stored == null || !value.equals(stored.trim())) {
                    continue;
                }
                switch (row.getListType()) {
                    case "BLACK" -> blackHit = true;
                    case "WATCH" -> watchHit = true;
                    case "WHITE" -> {
                        whiteHit = true;
                        if (row.getImmuneRuleId() == null) {
                            whiteImmuneAll = true;
                        } else if (!immuneRuleIds.contains(row.getImmuneRuleId())) {
                            immuneRuleIds.add(row.getImmuneRuleId());
                        }
                    }
                    default -> {
                    }
                }
            }
        }
        return new ListCheckSummary(blackHit, watchHit, whiteHit, whiteImmuneAll, List.copyOf(immuneRuleIds));
    }

    private static boolean isActive(StandaloneListRecordPO row, LocalDateTime now) {
        if (row.getEnabled() == null || row.getEnabled() != 1) {
            return false;
        }
        return row.getExpireAt() == null || !row.getExpireAt().isBefore(now);
    }
}
