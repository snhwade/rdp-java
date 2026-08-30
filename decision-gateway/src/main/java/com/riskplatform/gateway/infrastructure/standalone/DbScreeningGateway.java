package com.riskplatform.gateway.infrastructure.standalone;

import com.riskplatform.gateway.domain.ScreeningGateway;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Component
@ConditionalOnProperty(name = "rdp.integration.mode", havingValue = "standalone", matchIfMissing = true)
public class DbScreeningGateway implements ScreeningGateway {

    private static final double THRESHOLD = 0.85;
    private static final Set<String> NAME_DIMENSIONS = Set.of(
            "subjectName", "name", "payerName", "counterpartyName");

    private final StandaloneListRecordMapper mapper;

    public DbScreeningGateway(StandaloneListRecordMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public HitKind screenName(String subjectName) {
        if (subjectName == null || subjectName.isBlank()) {
            return HitKind.NONE;
        }
        LocalDateTime now = LocalDateTime.now();
        List<StandaloneListRecordPO> rows = mapper.selectList(null);
        String bestType = null;
        double bestSim = -1.0;
        for (StandaloneListRecordPO row : rows) {
            if (!isActive(row, now) || row.getDimensionValue() == null) {
                continue;
            }
            if (!NAME_DIMENSIONS.contains(row.getDimension())) {
                continue;
            }
            if ("WHITE".equals(row.getListType())) {
                continue;
            }
            double sim = NameSimilarity.similarity(subjectName, row.getDimensionValue());
            if (sim > bestSim) {
                bestSim = sim;
                bestType = row.getListType();
            }
        }
        if (bestSim < THRESHOLD || bestType == null) {
            return HitKind.NONE;
        }
        return "WATCH".equalsIgnoreCase(bestType) ? HitKind.WATCH : HitKind.BLACK;
    }

    private static boolean isActive(StandaloneListRecordPO row, LocalDateTime now) {
        if (row.getEnabled() == null || row.getEnabled() != 1) {
            return false;
        }
        return row.getExpireAt() == null || !row.getExpireAt().isBefore(now);
    }

    /** 与 screening-service 一致的 Levenshtein 相似度。 */
    static final class NameSimilarity {
        private NameSimilarity() {
        }

        static double similarity(String a, String b) {
            String x = normalize(a);
            String y = normalize(b);
            if (x.isEmpty() && y.isEmpty()) {
                return 1.0;
            }
            if (x.isEmpty() || y.isEmpty()) {
                return 0.0;
            }
            int distance = levenshtein(x, y);
            int maxLen = Math.max(x.length(), y.length());
            return 1.0 - ((double) distance / maxLen);
        }

        private static String normalize(String s) {
            return s == null ? "" : s.trim().toLowerCase();
        }

        private static int levenshtein(String s, String t) {
            int[] prev = new int[t.length() + 1];
            int[] curr = new int[t.length() + 1];
            for (int j = 0; j <= t.length(); j++) {
                prev[j] = j;
            }
            for (int i = 1; i <= s.length(); i++) {
                curr[0] = i;
                for (int j = 1; j <= t.length(); j++) {
                    int cost = s.charAt(i - 1) == t.charAt(j - 1) ? 0 : 1;
                    curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
                }
                int[] tmp = prev;
                prev = curr;
                curr = tmp;
            }
            return prev[t.length()];
        }
    }
}
