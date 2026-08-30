package com.riskplatform.screening.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 名单筛查与相似度匹配单元测试（R11.1/R11.2/R11.3）。
 */
class ScreeningMatcherTest {

    private final ScreeningMatcher matcher = new ScreeningMatcher();

    @Test
    void exactMatch_hitsWithSimilarity1() {
        List<ScreeningListEntry> entries = List.of(
                new ScreeningListEntry(1L, com.riskplatform.screening.domain.list.ListType.BLACK, "John Doe"));
        ScreeningResult r = matcher.screen("John Doe", entries, 0.85);
        assertThat(r.hit()).isTrue();
        assertThat(r.source()).isEqualTo(ListSource.SANCTION);
        assertThat(r.similarity()).isEqualTo(1.0);
        assertThat(r.matchedEntryId()).isEqualTo(1L);
    }

    @Test
    void caseInsensitive_andTrim() {
        List<ScreeningListEntry> entries = List.of(
                new ScreeningListEntry(1L, com.riskplatform.screening.domain.list.ListType.WATCH, "John Doe"));
        ScreeningResult r = matcher.screen("  john doe ", entries, 0.85);
        assertThat(r.hit()).isTrue();
    }

    @Test
    void belowThreshold_misses() {
        List<ScreeningListEntry> entries = List.of(
                new ScreeningListEntry(1L, com.riskplatform.screening.domain.list.ListType.BLACK, "Alibaba Group"));
        ScreeningResult r = matcher.screen("Tencent Holdings", entries, 0.85);
        assertThat(r.hit()).isFalse();
        assertThat(r.source()).isNull();
    }

    @Test
    void nearMatch_aboveThreshold_hits() {
        List<ScreeningListEntry> entries = List.of(
                new ScreeningListEntry(1L, com.riskplatform.screening.domain.list.ListType.BLACK, "Johnn Doe"));
        // "John Doe"(8) vs "Johnn Doe"(9): distance 1, maxLen 9 -> sim ~0.889
        ScreeningResult r = matcher.screen("John Doe", entries, 0.85);
        assertThat(r.hit()).isTrue();
        assertThat(r.similarity()).isGreaterThanOrEqualTo(0.85);
    }

    @Test
    void picksHighestSimilarityEntry() {
        List<ScreeningListEntry> entries = List.of(
                new ScreeningListEntry(1L, com.riskplatform.screening.domain.list.ListType.WATCH, "Jon Doe"),
                new ScreeningListEntry(2L, com.riskplatform.screening.domain.list.ListType.BLACK, "John Doe", 99L));
        ScreeningResult r = matcher.screen("John Doe", entries, 0.5);
        assertThat(r.matchedEntry()).isEqualTo("John Doe");
        assertThat(r.source()).isEqualTo(ListSource.SANCTION);
        assertThat(r.libraryId()).isEqualTo(99L);
        assertThat(r.matchedEntryId()).isEqualTo(2L);
    }

    @Test
    void emptyEntries_misses() {
        ScreeningResult r = matcher.screen("anyone", List.of(), 0.85);
        assertThat(r.hit()).isFalse();
        assertThat(r.similarity()).isEqualTo(0.0);
    }
}
