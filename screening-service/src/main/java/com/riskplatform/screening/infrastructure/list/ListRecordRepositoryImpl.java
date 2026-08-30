package com.riskplatform.screening.infrastructure.list;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.riskplatform.screening.domain.list.ListRecord;
import com.riskplatform.screening.domain.list.ListRecordRepository;
import com.riskplatform.screening.domain.list.ListType;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** 名单记录仓储 MyBatis-Plus 实现（S1）。 */
@Repository
public class ListRecordRepositoryImpl implements ListRecordRepository {

    private final ListRecordMapper mapper;

    public ListRecordRepositoryImpl(ListRecordMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public ListRecord save(ListRecord record) {
        ListRecordPO po = toPO(record);
        mapper.insert(po);
        return toDomain(po);
    }

    @Override
    public ListRecord update(ListRecord record) {
        mapper.updateById(toPO(record));
        return record;
    }

    @Override
    public boolean deleteById(Long id) {
        return mapper.deleteById(id) > 0;
    }

    @Override
    public Optional<ListRecord> findById(Long id) {
        return Optional.ofNullable(mapper.selectById(id)).map(this::toDomain);
    }

    @Override
    public List<ListRecord> findByType(ListType listType) {
        return mapper.selectList(new LambdaQueryWrapper<ListRecordPO>()
                        .eq(ListRecordPO::getListType, listType.name()))
                .stream().map(this::toDomain).toList();
    }

    @Override
    public List<ListRecord> findAll() {
        return mapper.selectList(new LambdaQueryWrapper<>()).stream().map(this::toDomain).toList();
    }

    @Override
    public List<ListRecord> findActiveByDimensionValue(String dimension, String dimensionValue) {
        LocalDateTime now = LocalDateTime.now();
        // dimensionValue 落库为 AES-GCM 密文（随机 IV，非确定性），无法在 SQL 层等值匹配（R17.4）。
        // 故仅按非敏感的 dimension + enabled 在 DB 过滤，再于内存中按解密后的明文值精确匹配。
        return mapper.selectList(new LambdaQueryWrapper<ListRecordPO>()
                        .eq(ListRecordPO::getDimension, dimension)
                        .eq(ListRecordPO::getEnabled, 1))
                .stream()
                .map(this::toDomain)
                .filter(r -> dimensionValue == null
                        ? r.dimensionValue() == null
                        : dimensionValue.equals(r.dimensionValue()))
                .filter(r -> r.isActiveAt(now))
                .toList();
    }

    private ListRecordPO toPO(ListRecord r) {
        ListRecordPO po = new ListRecordPO();
        po.setId(r.id());
        po.setListType(r.listType().name());
        po.setDimension(r.dimension());
        po.setDimensionValue(r.dimensionValue());
        po.setReason(r.reason());
        po.setImmuneRuleId(r.immuneRuleId());
        po.setExpireAt(r.expireAt());
        po.setEnabled(r.enabled() ? 1 : 0);
        return po;
    }

    private ListRecord toDomain(ListRecordPO po) {
        return new ListRecord(
                po.getId(),
                ListType.valueOf(po.getListType()),
                po.getDimension(),
                po.getDimensionValue(),
                po.getReason(),
                po.getImmuneRuleId(),
                po.getExpireAt(),
                po.getEnabled() == null || po.getEnabled() == 1);
    }
}
