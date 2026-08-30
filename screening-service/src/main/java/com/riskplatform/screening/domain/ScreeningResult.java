package com.riskplatform.screening.domain;

/**
 * 筛查结果（R11.2/R11.3）。
 *
 * @param hit             是否命中（相似度 ≥ 阈值）
 * @param listType        命中的名单类型（BLACK→拦截，WATCH→复核；未命中为 null）
 * @param source          命中的名单来源（未命中为 null）
 * @param matchedEntry    匹配的名单条目名称（未命中为 null）
 * @param similarity      匹配相似度（未命中为最高相似度，便于排查）
 * @param matchedEntryId  匹配条目 id（可跳转名单管理；legacy 可能有 id）
 * @param libraryId       所属名单库 id（list_entry 命中时有值）
 */
public record ScreeningResult(
        boolean hit,
        com.riskplatform.screening.domain.list.ListType listType,
        ListSource source,
        String matchedEntry,
        double similarity,
        Long matchedEntryId,
        Long libraryId) {

    public static ScreeningResult miss(double bestSimilarity) {
        return new ScreeningResult(false, null, null, null, bestSimilarity, null, null);
    }

    public static ScreeningResult of(
            com.riskplatform.screening.domain.list.ListType listType,
            ListSource source,
            String matchedEntry,
            double similarity,
            Long matchedEntryId,
            Long libraryId) {
        return new ScreeningResult(true, listType, source, matchedEntry, similarity, matchedEntryId, libraryId);
    }
}
