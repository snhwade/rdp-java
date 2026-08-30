package com.riskplatform.screening.domain;

import java.util.List;

/**
 * 名称筛查匹配器（R11.1/R11.2/R11.3）。
 *
 * <p>依次对名单/制裁/道琼斯类名单进行相似度匹配：
 * 当主体名称与某条目相似度 ≥ 阈值即命中，返回命中来源、匹配条目与相似度；
 * 全部条目相似度均小于阈值则未命中（返回最高相似度便于排查）。
 *
 * <p>命中时优先返回相似度最高的条目；相似度相同时按名单来源枚举次序（WATCHLIST→SANCTION→DOW_JONES）
 * 保证确定性。
 */
public final class ScreeningMatcher {

    /**
     * 对主体名称在给定名单条目中进行筛查。
     *
     * @param subjectName 被筛查的主体名称
     * @param entries     名单条目（可跨多来源）
     * @param threshold   相似度阈值 [0,1]
     * @return 筛查结果
     */
    public ScreeningResult screen(String subjectName, List<ScreeningListEntry> entries, double threshold) {
        ScreeningListEntry best = null;
        double bestSim = -1.0;
        for (ScreeningListEntry entry : entries) {
            double sim = NameSimilarity.similarity(subjectName, entry.entryName());
            if (sim > bestSim
                    || (sim == bestSim && best != null && entry.listType().ordinal() < best.listType().ordinal())) {
                bestSim = sim;
                best = entry;
            }
        }
        if (best != null && bestSim >= threshold) {
            ListSource source = mapListSource(best.listType());
            return ScreeningResult.of(best.listType(), source, best.entryName(), bestSim,
                    best.id(), best.libraryId());
        }
        return ScreeningResult.miss(Math.max(bestSim, 0.0));
    }

    private static ListSource mapListSource(com.riskplatform.screening.domain.list.ListType type) {
        if (type == com.riskplatform.screening.domain.list.ListType.BLACK) {
            return ListSource.SANCTION;
        }
        return ListSource.WATCHLIST;
    }
}
