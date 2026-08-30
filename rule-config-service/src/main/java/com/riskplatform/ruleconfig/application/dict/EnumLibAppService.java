package com.riskplatform.ruleconfig.application.dict;

import com.riskplatform.common.error.BizException;
import com.riskplatform.ruleconfig.domain.dict.DictReferenceChecker;
import com.riskplatform.ruleconfig.domain.enums.EnumDataType;
import com.riskplatform.ruleconfig.domain.enums.EnumLib;
import com.riskplatform.ruleconfig.domain.enums.EnumLibRepository;
import com.riskplatform.ruleconfig.domain.enums.EnumValue;
import com.riskplatform.ruleconfig.domain.enums.EnumValueRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 枚举库应用服务（R12.2/R12.4）。
 *
 * <p>编排枚举库与枚举值的 CRUD、导入导出与删除引用校验：
 * <ul>
 *   <li>枚举库：创建（code 唯一）、更新、删除（无引用且先级联删除枚举值，R12.4）；</li>
 *   <li>枚举值：新增/更新/删除（同库内 value 唯一），删除前校验引用（R12.4）；</li>
 *   <li>导入：批量 upsert 枚举值（按 value 去重，已存在则更新 label/order）；</li>
 *   <li>导出：返回枚举库下全部枚举值（按 order_no 升序）。</li>
 * </ul>
 *
 * <p>本服务以组件扫描方式自注册（{@code @Service}），不在共享配置类中装配。
 */
@Service
public class EnumLibAppService {

    private final EnumLibRepository enumLibRepository;
    private final EnumValueRepository enumValueRepository;
    private final DictReferenceChecker referenceChecker;

    public EnumLibAppService(EnumLibRepository enumLibRepository,
                             EnumValueRepository enumValueRepository,
                             DictReferenceChecker referenceChecker) {
        this.enumLibRepository = enumLibRepository;
        this.enumValueRepository = enumValueRepository;
        this.referenceChecker = referenceChecker;
    }

    // ============================ 枚举库 ============================

    /** 创建枚举库（R12.2）。 */
    public EnumLib createLib(String code, String name, String dataType) {
        EnumLib lib = EnumLib.create(code, name, parseDataType(dataType));
        if (enumLibRepository.existsByCode(code)) {
            throw BizException.duplicate("枚举库 code 已存在: " + code);
        }
        return enumLibRepository.save(lib);
    }

    /** 更新枚举库（R12.2）。 */
    public EnumLib updateLib(Long id, String name, String dataType, String status) {
        EnumLib lib = enumLibRepository.findById(id)
                .orElseThrow(() -> BizException.notFound("枚举库不存在: id=" + id));
        EnumDataType dt = (dataType == null || dataType.isBlank()) ? null : parseDataType(dataType);
        lib.update(name, dt, parseStatus(status));
        enumLibRepository.update(lib);
        return lib;
    }

    /** 列表查询枚举库（R12.2）。 */
    public List<EnumLib> listLibs() {
        return enumLibRepository.findAll();
    }

    /** 查询单个枚举库（R12.2）。 */
    public EnumLib getLib(Long id) {
        return enumLibRepository.findById(id)
                .orElseThrow(() -> BizException.notFound("枚举库不存在: id=" + id));
    }

    /** 删除枚举库：校验引用后级联删除枚举值（R12.4）。 */
    @Transactional
    public void deleteLib(Long id) {
        EnumLib lib = enumLibRepository.findById(id)
                .orElseThrow(() -> BizException.notFound("枚举库不存在: id=" + id));
        if (referenceChecker.isEnumLibReferenced(lib.getCode())) {
            throw BizException.invalidState("枚举库已被引用，无法删除: " + lib.getCode());
        }
        enumValueRepository.deleteByLibId(id);
        enumLibRepository.deleteById(id);
    }

    // ============================ 枚举值 ============================

    /** 列出枚举库下全部枚举值（R12.2/R12.3）。 */
    public List<EnumValue> listValues(Long enumLibId) {
        ensureLibExists(enumLibId);
        return enumValueRepository.findByLibId(enumLibId);
    }

    /** 新增枚举值（同库内 value 唯一，R12.2）。 */
    public EnumValue addValue(Long enumLibId, String value, String label, int orderNo) {
        ensureLibExists(enumLibId);
        if (enumValueRepository.existsByLibAndValue(enumLibId, value)) {
            throw BizException.duplicate("枚举值已存在: " + value);
        }
        EnumValue v = EnumValue.create(enumLibId, value, label, orderNo);
        return enumValueRepository.save(v);
    }

    /** 更新枚举值（label/order，R12.2）。 */
    public EnumValue updateValue(Long valueId, String label, int orderNo) {
        EnumValue existing = enumValueRepository.findById(valueId)
                .orElseThrow(() -> BizException.notFound("枚举值不存在: id=" + valueId));
        EnumValue updated = EnumValue.rehydrate(existing.getId(), existing.getEnumLibId(),
                existing.getValue(), label, orderNo);
        updated.validate();
        enumValueRepository.update(updated);
        return updated;
    }

    /** 删除枚举值，删除前校验引用关系（R12.4）。 */
    public void deleteValue(Long valueId) {
        EnumValue v = enumValueRepository.findById(valueId)
                .orElseThrow(() -> BizException.notFound("枚举值不存在: id=" + valueId));
        EnumLib lib = enumLibRepository.findById(v.getEnumLibId())
                .orElseThrow(() -> BizException.notFound("枚举库不存在: id=" + v.getEnumLibId()));
        if (referenceChecker.isEnumValueReferenced(lib.getCode(), v.getValue())) {
            throw BizException.invalidState("枚举值已被引用，无法删除: " + v.getValue());
        }
        enumValueRepository.deleteById(valueId);
    }

    /**
     * 批量导入枚举值（R12.2）。按 value upsert：已存在则更新 label/order，不存在则新增。
     *
     * @return 导入后该枚举库的全部枚举值
     */
    @Transactional
    public List<EnumValue> importValues(Long enumLibId, List<ImportItem> items) {
        ensureLibExists(enumLibId);
        if (items == null) {
            return enumValueRepository.findByLibId(enumLibId);
        }
        for (ImportItem item : items) {
            // 先构造领域对象做校验（value 非空/长度等）
            EnumValue candidate = EnumValue.create(enumLibId, item.value(), item.label(), item.orderNo());
            enumValueRepository.findByLibAndValue(enumLibId, item.value())
                    .ifPresentOrElse(existing -> {
                        EnumValue merged = EnumValue.rehydrate(existing.getId(), enumLibId,
                                existing.getValue(), item.label(), item.orderNo());
                        enumValueRepository.update(merged);
                    }, () -> enumValueRepository.save(candidate));
        }
        return enumValueRepository.findByLibId(enumLibId);
    }

    /** 导出枚举值（R12.2）：等价于列出全部枚举值，供前端下载。 */
    public List<EnumValue> exportValues(Long enumLibId) {
        return listValues(enumLibId);
    }

    private void ensureLibExists(Long enumLibId) {
        if (enumLibRepository.findById(enumLibId).isEmpty()) {
            throw BizException.notFound("枚举库不存在: id=" + enumLibId);
        }
    }

    private EnumDataType parseDataType(String dataType) {
        if (dataType == null || dataType.isBlank()) {
            throw BizException.invalidState("枚举库数据类型必填");
        }
        try {
            return EnumDataType.valueOf(dataType);
        } catch (IllegalArgumentException ex) {
            throw BizException.invalidState("非法数据类型: " + dataType);
        }
    }

    private EnumLib.EnumStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return EnumLib.EnumStatus.valueOf(status);
        } catch (IllegalArgumentException ex) {
            throw BizException.invalidState("非法状态值: " + status);
        }
    }

    /** 导入项：枚举值 + 标签 + 排序号。 */
    public record ImportItem(String value, String label, int orderNo) {
    }
}
