package com.riskplatform.screening.infrastructure;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.riskplatform.common.web.GlobalExceptionHandler;
import com.riskplatform.common.crypto.FieldCryptoConfig;
import com.riskplatform.screening.adapter.ScreeningController;
import com.riskplatform.screening.application.ScreeningService;
import com.riskplatform.screening.application.list.ListManagementService;
import com.riskplatform.screening.domain.ScreeningMatcher;
import com.riskplatform.screening.domain.list.ListRecordRepository;
import com.riskplatform.screening.domain.list.ListType;
import com.riskplatform.screening.infrastructure.listmgmt.ListDimensionMapper;
import com.riskplatform.screening.infrastructure.listmgmt.ListDimensionPO;
import com.riskplatform.screening.infrastructure.listmgmt.ListEntryMapper;
import com.riskplatform.screening.infrastructure.listmgmt.ListEntryPO;
import com.riskplatform.screening.infrastructure.listmgmt.ListLibraryMapper;
import com.riskplatform.screening.infrastructure.listmgmt.ListLibraryPO;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 筛查服务装配（R11 + S1 名单管理）。
 */
@Configuration
@Import({GlobalExceptionHandler.class, FieldCryptoConfig.class})
public class ScreeningConfig {

    /** 参与名称模糊筛查的 dimension（精确名单如 merchantId 不走模糊匹配）。 */
    private static final Set<String> NAME_DIMENSIONS = Set.of(
            "subjectName", "name", "payerName", "counterpartyName");

    @Bean
    public ScreeningMatcher screeningMatcher() {
        return new ScreeningMatcher();
    }

    @Bean
    public ScreeningService screeningService(ScreeningMatcher matcher) {
        return new ScreeningService(matcher);
    }

    @Bean
    public ListManagementService listManagementService(ListRecordRepository repository) {
        return new ListManagementService(repository);
    }

    /**
     * 名单加载（S1 + LS1）：合并 legacy {@code list_record} 与名单库 {@code list_entry}
     *（启用库 + 启用且未过期条目 + 名称/模糊维度），供相似度筛查；list_entry 携带 libraryId 以便跳转。
     */
    @Bean
    public ScreeningController.ScreeningListProvider screeningListProvider(
            ListRecordRepository repository,
            ListEntryMapper entryMapper,
            ListLibraryMapper libraryMapper,
            ListDimensionMapper dimensionMapper) {
        return () -> {
            List<com.riskplatform.screening.domain.ScreeningListEntry> out = new ArrayList<>();

            repository.findAll().stream()
                    .filter(r -> r.isActiveAt(LocalDateTime.now()))
                    .filter(r -> r.listType() != ListType.WHITE)
                    .filter(r -> NAME_DIMENSIONS.contains(r.dimension()))
                    .map(r -> new com.riskplatform.screening.domain.ScreeningListEntry(
                            r.id(), r.listType(), r.dimensionValue(), null))
                    .forEach(out::add);

            Set<String> fuzzyDims = new HashSet<>(NAME_DIMENSIONS);
            dimensionMapper.selectList(new LambdaQueryWrapper<ListDimensionPO>()
                            .eq(ListDimensionPO::getFuzzyEnabled, 1))
                    .stream()
                    .map(ListDimensionPO::getCode)
                    .forEach(fuzzyDims::add);

            Set<Long> enabledLibraryIds = libraryMapper.selectList(new LambdaQueryWrapper<ListLibraryPO>()
                            .eq(ListLibraryPO::getEnabled, 1))
                    .stream()
                    .map(ListLibraryPO::getId)
                    .collect(Collectors.toSet());
            if (enabledLibraryIds.isEmpty() || fuzzyDims.isEmpty()) {
                return out;
            }

            LocalDateTime now = LocalDateTime.now();
            entryMapper.selectList(new LambdaQueryWrapper<ListEntryPO>()
                            .in(ListEntryPO::getLibraryId, enabledLibraryIds)
                            .eq(ListEntryPO::getEnabled, 1)
                            .in(ListEntryPO::getDimensionCode, fuzzyDims))
                    .stream()
                    .filter(e -> isActiveAt(e, now))
                    .map(e -> new com.riskplatform.screening.domain.ScreeningListEntry(
                            e.getId(), ListType.WATCH, e.getDimensionValue(), e.getLibraryId()))
                    .forEach(out::add);

            return out;
        };
    }

    private static boolean isActiveAt(ListEntryPO e, LocalDateTime now) {
        if (e.getEffectiveAt() != null && now.isBefore(e.getEffectiveAt())) {
            return false;
        }
        if (e.getExpireAt() != null && now.isAfter(e.getExpireAt())) {
            return false;
        }
        return true;
    }
}
