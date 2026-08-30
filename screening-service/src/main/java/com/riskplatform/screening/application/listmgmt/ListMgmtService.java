package com.riskplatform.screening.application.listmgmt;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.riskplatform.common.error.BizException;
import com.riskplatform.screening.infrastructure.listmgmt.ListAttrDefMapper;
import com.riskplatform.screening.infrastructure.listmgmt.ListAttrDefPO;
import com.riskplatform.screening.infrastructure.listmgmt.ListDimensionMapper;
import com.riskplatform.screening.infrastructure.listmgmt.ListDimensionPO;
import com.riskplatform.screening.infrastructure.listmgmt.ListEntryMapper;
import com.riskplatform.screening.infrastructure.listmgmt.ListEntryPO;
import com.riskplatform.screening.infrastructure.listmgmt.ListImportAuditMapper;
import com.riskplatform.screening.infrastructure.listmgmt.ListImportAuditPO;
import com.riskplatform.screening.infrastructure.listmgmt.ListLibraryMapper;
import com.riskplatform.screening.infrastructure.listmgmt.ListLibraryPO;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** 名单库 / 维度 / 附加属性 / 库内记录应用服务。 */
@Service
public class ListMgmtService {

    /** 即将失效窗口（天）。 */
    public static final int EXPIRING_SOON_DAYS = 7;

    private final ListDimensionMapper dimensionMapper;
    private final ListLibraryMapper libraryMapper;
    private final ListAttrDefMapper attrDefMapper;
    private final ListEntryMapper entryMapper;
    private final ListImportAuditMapper importAuditMapper;
    private final ListLibraryReferenceChecker referenceChecker;

    public ListMgmtService(ListDimensionMapper dimensionMapper,
                           ListLibraryMapper libraryMapper,
                           ListAttrDefMapper attrDefMapper,
                           ListEntryMapper entryMapper,
                           ListImportAuditMapper importAuditMapper,
                           ListLibraryReferenceChecker referenceChecker) {
        this.dimensionMapper = dimensionMapper;
        this.libraryMapper = libraryMapper;
        this.attrDefMapper = attrDefMapper;
        this.entryMapper = entryMapper;
        this.importAuditMapper = importAuditMapper;
        this.referenceChecker = referenceChecker;
    }

    // —— 维度 ——

    public List<ListDimensionPO> listDimensions(String keyword) {
        LambdaQueryWrapper<ListDimensionPO> q = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            q.and(w -> w.like(ListDimensionPO::getCode, keyword)
                    .or().like(ListDimensionPO::getName, keyword));
        }
        q.orderByAsc(ListDimensionPO::getCode);
        return dimensionMapper.selectList(q);
    }

    public ListDimensionPO createDimension(String code, String name, String maskRule,
                                           boolean fuzzyEnabled, String updatedBy) {
        if (dimensionMapper.selectOne(new LambdaQueryWrapper<ListDimensionPO>()
                .eq(ListDimensionPO::getCode, code)) != null) {
            throw BizException.duplicate("维度编码已存在: " + code);
        }
        ListDimensionPO po = new ListDimensionPO();
        po.setCode(code);
        po.setName(name);
        po.setMaskRule(maskRule == null ? "NONE" : maskRule);
        po.setFuzzyEnabled(fuzzyEnabled ? 1 : 0);
        po.setUpdatedBy(updatedBy);
        dimensionMapper.insert(po);
        return po;
    }

    public ListDimensionPO updateDimension(Long id, String name, String maskRule,
                                           Boolean fuzzyEnabled, String updatedBy) {
        ListDimensionPO po = requireDimension(id);
        if (name != null) po.setName(name);
        if (maskRule != null) po.setMaskRule(maskRule);
        if (fuzzyEnabled != null) po.setFuzzyEnabled(fuzzyEnabled ? 1 : 0);
        if (updatedBy != null) po.setUpdatedBy(updatedBy);
        dimensionMapper.updateById(po);
        return po;
    }

    public void deleteDimensions(List<Long> ids) {
        for (Long id : ids) {
            dimensionMapper.deleteById(id);
        }
    }

    private ListDimensionPO requireDimension(Long id) {
        return Optional.ofNullable(dimensionMapper.selectById(id))
                .orElseThrow(() -> BizException.notFound("维度不存在: id=" + id));
    }

    // —— 名单库 ——

    public List<ListLibraryPO> listLibraries(String keyword) {
        LambdaQueryWrapper<ListLibraryPO> q = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            q.and(w -> w.like(ListLibraryPO::getCode, keyword)
                    .or().like(ListLibraryPO::getName, keyword));
        }
        q.orderByDesc(ListLibraryPO::getUpdatedAt);
        return libraryMapper.selectList(q);
    }

    public ListLibraryPO createLibrary(String code, String name, String description, String remark) {
        if (libraryMapper.selectOne(new LambdaQueryWrapper<ListLibraryPO>()
                .eq(ListLibraryPO::getCode, code)) != null) {
            throw BizException.duplicate("名单库编码已存在: " + code);
        }
        ListLibraryPO po = new ListLibraryPO();
        po.setCode(code);
        po.setName(name);
        po.setDescription(description);
        po.setRemark(remark);
        po.setEnabled(1);
        libraryMapper.insert(po);
        return po;
    }

    public ListLibraryPO updateLibrary(Long id, String name, String description, String remark, Boolean enabled) {
        ListLibraryPO po = requireLibrary(id);
        if (Boolean.FALSE.equals(enabled)) {
            assertNotReferenced(po.getCode(), "停用");
        }
        if (name != null) po.setName(name);
        if (description != null) po.setDescription(description);
        if (remark != null) po.setRemark(remark);
        if (enabled != null) po.setEnabled(enabled ? 1 : 0);
        libraryMapper.updateById(po);
        return po;
    }

    public void deleteLibrary(Long id) {
        ListLibraryPO po = requireLibrary(id);
        assertNotReferenced(po.getCode(), "删除");
        entryMapper.delete(new LambdaQueryWrapper<ListEntryPO>().eq(ListEntryPO::getLibraryId, id));
        libraryMapper.deleteById(id);
    }

    public ListLibraryPO requireLibrary(Long id) {
        return Optional.ofNullable(libraryMapper.selectById(id))
                .orElseThrow(() -> BizException.notFound("名单库不存在: id=" + id));
    }

    public List<String> listLibraryReferences(Long id) {
        ListLibraryPO po = requireLibrary(id);
        return referenceChecker.findReferences(po.getCode());
    }

    public long countEntries(Long libraryId) {
        return entryMapper.selectCount(new LambdaQueryWrapper<ListEntryPO>()
                .eq(ListEntryPO::getLibraryId, libraryId));
    }

    /** 库级通用统计（LM1）：总数 / 启用数 / 即将失效数。 */
    public LibraryStats libraryStats(Long libraryId) {
        requireLibrary(libraryId);
        List<ListEntryPO> all = entryMapper.selectList(new LambdaQueryWrapper<ListEntryPO>()
                .eq(ListEntryPO::getLibraryId, libraryId));
        long total = all.size();
        long enabled = all.stream().filter(e -> e.getEnabled() != null && e.getEnabled() == 1).count();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime soon = now.plusDays(EXPIRING_SOON_DAYS);
        long expiringSoon = all.stream()
                .filter(e -> e.getEnabled() != null && e.getEnabled() == 1)
                .filter(e -> e.getExpireAt() != null)
                .filter(e -> !e.getExpireAt().isBefore(now) && !e.getExpireAt().isAfter(soon))
                .count();
        return new LibraryStats(total, enabled, expiringSoon, EXPIRING_SOON_DAYS);
    }

    /**
     * 外部同步占位（LX1）：只写审计，不落地具体外部源（LX2）。
     */
    public SyncStubResult syncLibraryStub(Long libraryId, String source, String batchId, Integer entryCount) {
        requireLibrary(libraryId);
        String src = StringUtils.hasText(source) ? source.trim() : "EXTERNAL_STUB";
        String batch = StringUtils.hasText(batchId) ? batchId.trim() : UUID.randomUUID().toString().replace("-", "");
        int count = entryCount == null ? 0 : Math.max(0, entryCount);
        ListImportAuditPO audit = new ListImportAuditPO();
        audit.setLibraryId(libraryId);
        audit.setSource(src);
        audit.setBatchId(batch);
        audit.setEntryCount(count);
        audit.setStatus("STUB_RECORDED");
        audit.setMessage("外部名单源对接尚未启用（LX2）；已记录同步请求审计");
        importAuditMapper.insert(audit);
        return new SyncStubResult(audit.getId(), batch, audit.getStatus(), audit.getMessage(), count);
    }

    public List<ListImportAuditPO> listImportAudits(Long libraryId, int limit) {
        requireLibrary(libraryId);
        int lim = limit <= 0 ? 20 : Math.min(limit, 100);
        return importAuditMapper.selectList(new LambdaQueryWrapper<ListImportAuditPO>()
                .eq(ListImportAuditPO::getLibraryId, libraryId)
                .orderByDesc(ListImportAuditPO::getCreatedAt)
                .last("LIMIT " + lim));
    }

    private void assertNotReferenced(String libraryCode, String action) {
        List<String> refs = referenceChecker.findReferences(libraryCode);
        if (!refs.isEmpty()) {
            throw BizException.invalidState(action + "失败，名单库仍被引用：" + String.join("、", refs)
                    + "。请先解除引用后再操作。");
        }
    }

    // —— 附加属性 ——

    public List<ListAttrDefPO> listAttrDefs(String keyword) {
        LambdaQueryWrapper<ListAttrDefPO> q = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            q.and(w -> w.like(ListAttrDefPO::getCode, keyword)
                    .or().like(ListAttrDefPO::getName, keyword));
        }
        q.orderByAsc(ListAttrDefPO::getCode);
        return attrDefMapper.selectList(q);
    }

    public ListAttrDefPO createAttrDef(String code, String name, String inputType,
                                       boolean required, boolean multiValue, String maskRule) {
        if (attrDefMapper.selectOne(new LambdaQueryWrapper<ListAttrDefPO>()
                .eq(ListAttrDefPO::getCode, code)) != null) {
            throw BizException.duplicate("属性编码已存在: " + code);
        }
        ListAttrDefPO po = new ListAttrDefPO();
        po.setCode(code);
        po.setName(name);
        po.setInputType(inputType == null ? "TEXT" : inputType);
        po.setRequired(required ? 1 : 0);
        po.setMultiValue(multiValue ? 1 : 0);
        po.setMaskRule(maskRule == null ? "NONE" : maskRule);
        attrDefMapper.insert(po);
        return po;
    }

    public ListAttrDefPO updateAttrDef(Long id, String name, String inputType,
                                       Boolean required, Boolean multiValue, String maskRule) {
        ListAttrDefPO po = requireAttrDef(id);
        if (name != null) po.setName(name);
        if (inputType != null) po.setInputType(inputType);
        if (required != null) po.setRequired(required ? 1 : 0);
        if (multiValue != null) po.setMultiValue(multiValue ? 1 : 0);
        if (maskRule != null) po.setMaskRule(maskRule);
        attrDefMapper.updateById(po);
        return po;
    }

    public void deleteAttrDefs(List<Long> ids) {
        for (Long id : ids) {
            attrDefMapper.deleteById(id);
        }
    }

    private ListAttrDefPO requireAttrDef(Long id) {
        return Optional.ofNullable(attrDefMapper.selectById(id))
                .orElseThrow(() -> BizException.notFound("附加属性不存在: id=" + id));
    }

    // —— 库内记录 ——

    public List<ListEntryPO> listEntries(Long libraryId, String dimensionCode, String keyword) {
        LambdaQueryWrapper<ListEntryPO> q = new LambdaQueryWrapper<ListEntryPO>()
                .eq(ListEntryPO::getLibraryId, libraryId);
        if (StringUtils.hasText(dimensionCode)) {
            q.eq(ListEntryPO::getDimensionCode, dimensionCode);
        }
        if (StringUtils.hasText(keyword)) {
            q.and(w -> w.like(ListEntryPO::getDimensionValue, keyword)
                    .or().like(ListEntryPO::getDimensionCode, keyword));
        }
        q.orderByDesc(ListEntryPO::getUpdatedAt);
        return entryMapper.selectList(q);
    }

    public ListEntryPO createEntry(Long libraryId, String dimensionCode, String dimensionValue,
                                   LocalDateTime effectiveAt, LocalDateTime expireAt,
                                   Map<String, Object> extraAttrs, String remark) {
        requireLibrary(libraryId);
        ListEntryPO po = new ListEntryPO();
        po.setLibraryId(libraryId);
        po.setDimensionCode(dimensionCode);
        po.setDimensionValue(dimensionValue);
        po.setEffectiveAt(effectiveAt);
        po.setExpireAt(expireAt);
        po.setEnabled(1);
        po.setSource("MANUAL");
        po.setRemark(remark);
        po.setExtraAttrs(extraAttrs);
        entryMapper.insert(po);
        return po;
    }

    public ListEntryPO updateEntry(Long id, String dimensionValue, LocalDateTime effectiveAt,
                                   LocalDateTime expireAt, Boolean enabled,
                                   Map<String, Object> extraAttrs, String remark) {
        ListEntryPO po = requireEntry(id);
        if (dimensionValue != null) po.setDimensionValue(dimensionValue);
        if (effectiveAt != null) po.setEffectiveAt(effectiveAt);
        if (expireAt != null) po.setExpireAt(expireAt);
        if (enabled != null) po.setEnabled(enabled ? 1 : 0);
        if (extraAttrs != null) po.setExtraAttrs(extraAttrs);
        if (remark != null) po.setRemark(remark);
        entryMapper.updateById(po);
        return po;
    }

    public void deleteEntries(List<Long> ids) {
        for (Long id : ids) {
            entryMapper.deleteById(id);
        }
    }

    public void batchSetEnabled(List<Long> ids, boolean enabled) {
        for (Long id : ids) {
            ListEntryPO po = requireEntry(id);
            po.setEnabled(enabled ? 1 : 0);
            entryMapper.updateById(po);
        }
    }

    /** 命中判定：返回命中的库内记录（不区分黑/白，由上层引用决定语义）。 */
    public List<ListEntryPO> matchEntries(String libraryCode, String dimensionCode, String value) {
        ListLibraryPO library = libraryMapper.selectOne(new LambdaQueryWrapper<ListLibraryPO>()
                .eq(ListLibraryPO::getCode, libraryCode));
        if (library == null || library.getEnabled() == null || library.getEnabled() == 0) {
            return List.of();
        }
        LocalDateTime now = LocalDateTime.now();
        return entryMapper.selectList(new LambdaQueryWrapper<ListEntryPO>()
                        .eq(ListEntryPO::getLibraryId, library.getId())
                        .eq(ListEntryPO::getDimensionCode, dimensionCode)
                        .eq(ListEntryPO::getEnabled, 1))
                .stream()
                .filter(e -> value.equals(e.getDimensionValue()))
                .filter(e -> isActiveAt(e, now))
                .toList();
    }

    /** 跨库命中：按维度编码与值查找所有启用库内的命中记录。 */
    public List<LibraryHit> matchAllLibraries(String dimensionCode, String value) {
        LocalDateTime now = LocalDateTime.now();
        return entryMapper.selectList(new LambdaQueryWrapper<ListEntryPO>()
                        .eq(ListEntryPO::getDimensionCode, dimensionCode)
                        .eq(ListEntryPO::getEnabled, 1))
                .stream()
                .filter(e -> value.equals(e.getDimensionValue()))
                .filter(e -> isActiveAt(e, now))
                .map(e -> {
                    ListLibraryPO lib = libraryMapper.selectById(e.getLibraryId());
                    if (lib == null || lib.getEnabled() == null || lib.getEnabled() == 0) {
                        return null;
                    }
                    return new LibraryHit(lib.getId(), lib.getCode(), lib.getName(),
                            e.getId(), e.getDimensionCode(), e.getDimensionValue());
                })
                .filter(h -> h != null)
                .toList();
    }

    private ListEntryPO requireEntry(Long id) {
        return Optional.ofNullable(entryMapper.selectById(id))
                .orElseThrow(() -> BizException.notFound("名单记录不存在: id=" + id));
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

    public record LibraryHit(Long libraryId, String libraryCode, String libraryName,
                             Long entryId, String dimensionCode, String dimensionValue) {
    }

    public record LibraryStats(long total, long enabled, long expiringSoon, int expiringSoonDays) {
    }

    public record SyncStubResult(Long auditId, String batchId, String status, String message, int entryCount) {
    }
}
