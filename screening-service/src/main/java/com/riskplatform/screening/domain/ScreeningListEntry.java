package com.riskplatform.screening.domain;

/**
 * 名单条目（R11.2）。
 *
 * @param id         条目 id
 * @param listType   名单类型（BLACK/WATCH，参与模糊筛查）
 * @param entryName  名单中的名称（dimensionValue）
 * @param libraryId  所属名单库 id（list_entry 来源；legacy list_record 为 null）
 */
public record ScreeningListEntry(
        Long id,
        com.riskplatform.screening.domain.list.ListType listType,
        String entryName,
        Long libraryId) {

    public ScreeningListEntry(Long id, com.riskplatform.screening.domain.list.ListType listType, String entryName) {
        this(id, listType, entryName, null);
    }
}
