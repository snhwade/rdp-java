package com.riskplatform.rating.application;

import com.riskplatform.common.model.PagedResult;
import com.riskplatform.rating.domain.IncompleteRatingDataException;
import com.riskplatform.rating.domain.MerchantRating;
import com.riskplatform.rating.domain.MerchantRatingListView;
import com.riskplatform.rating.domain.MerchantRatingQuery;
import com.riskplatform.rating.domain.MerchantRatingRepository;
import com.riskplatform.rating.domain.RatingScorer;
import com.riskplatform.rating.domain.RatingStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 商户评级应用服务单元测试（R12.3/R12.4/R12.5）。
 */
class MerchantRatingServiceTest {

    static class InMemoryRepo implements MerchantRatingRepository {
        final Map<String, MerchantRating> store = new HashMap<>();
        final Map<String, LocalDateTime> updatedAt = new HashMap<>();

        @Override
        public void save(MerchantRating rating) {
            store.put(rating.getMerchantId(), rating);
            updatedAt.put(rating.getMerchantId(), LocalDateTime.now());
        }

        @Override
        public Optional<MerchantRating> findByMerchantId(String merchantId) {
            return Optional.ofNullable(store.get(merchantId));
        }

        @Override
        public PagedResult<MerchantRatingListView> query(MerchantRatingQuery query) {
            List<MerchantRatingListView> rows = new ArrayList<>();
            for (MerchantRating rating : store.values()) {
                if (query.merchantId() != null && !rating.getMerchantId().contains(query.merchantId())) {
                    continue;
                }
                if (query.status() != null && !rating.getStatus().name().equals(query.status())) {
                    continue;
                }
                if (query.level() != null
                        && (rating.getLevel() == null || !rating.getLevel().name().equals(query.level()))) {
                    continue;
                }
                rows.add(new MerchantRatingListView(
                        rating.getMerchantId(),
                        rating.getScore(),
                        rating.getLevel() == null ? null : rating.getLevel().name(),
                        rating.getStatus().name(),
                        updatedAt.getOrDefault(rating.getMerchantId(), LocalDateTime.MIN)));
            }
            rows.sort(Comparator.comparing(MerchantRatingListView::updatedAt).reversed());
            int from = (query.page() - 1) * query.pageSize();
            int to = Math.min(from + query.pageSize(), rows.size());
            List<MerchantRatingListView> pageRows = from >= rows.size() ? List.of() : rows.subList(from, to);
            return PagedResult.of(pageRows, query.page(), query.pageSize(), rows.size());
        }
    }

    private final RatingScorer scorer = new RatingScorer(Map.of("a", 10.0));

    @Test
    void computeAndSave_persistsRatedResult() {
        InMemoryRepo repo = new InMemoryRepo();
        MerchantRatingService service = new MerchantRatingService(repo, scorer);
        MerchantRating r = service.computeAndSave("M1", Map.of("a", 5.0)); // 50
        assertThat(r.getScore()).isEqualTo(50);
        assertThat(r.getStatus()).isEqualTo(RatingStatus.RATED);
        assertThat(repo.findByMerchantId("M1")).isPresent();
    }

    @Test
    void query_unratedMerchant_returnsUnrated() {
        MerchantRatingService service = new MerchantRatingService(new InMemoryRepo(), scorer);
        MerchantRating r = service.query("UNKNOWN");
        assertThat(r.getStatus()).isEqualTo(RatingStatus.UNRATED);
        assertThat(r.isRated()).isFalse();
    }

    @Test
    void computeAndSave_emptyFactors_keepsExistingAndThrows() {
        InMemoryRepo repo = new InMemoryRepo();
        MerchantRatingService service = new MerchantRatingService(repo, scorer);
        service.computeAndSave("M1", Map.of("a", 5.0));
        assertThatThrownBy(() -> service.computeAndSave("M1", new HashMap<>()))
                .isInstanceOf(IncompleteRatingDataException.class);
        // 既有评级保持不变
        assertThat(repo.findByMerchantId("M1").orElseThrow().getScore()).isEqualTo(50);
    }

    @Test
    void list_ordersByUpdatedAtDesc() throws InterruptedException {
        InMemoryRepo repo = new InMemoryRepo();
        MerchantRatingService service = new MerchantRatingService(repo, scorer);
        service.computeAndSave("M1", Map.of("a", 5.0));
        Thread.sleep(5);
        service.computeAndSave("M2", Map.of("a", 8.0));
        var result = service.list(new MerchantRatingQuery(null, null, null, null, null, 1, 20));
        assertThat(result.data()).hasSize(2);
        assertThat(result.data().get(0).merchantId()).isEqualTo("M2");
    }
}
