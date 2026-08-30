package com.riskplatform.ruleconfig.application.dict;

import com.riskplatform.common.error.BizException;
import com.riskplatform.ruleconfig.domain.dict.DecisionTag;
import com.riskplatform.ruleconfig.domain.dict.DecisionTagRepository;
import com.riskplatform.ruleconfig.domain.dict.DictReferenceChecker;
import com.riskplatform.ruleconfig.domain.dict.DictStatus;
import com.riskplatform.ruleconfig.domain.dict.RiskLevel;
import com.riskplatform.ruleconfig.domain.dict.RiskLevelRepository;
import com.riskplatform.ruleconfig.domain.dict.RiskType;
import com.riskplatform.ruleconfig.domain.dict.RiskTypeRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 字典应用服务（R12.1/R12.4）。
 *
 * <p>统一编排风险类型/风险等级/决策标签三类字典的 CRUD：
 * <ul>
 *   <li>创建：领域对象校验 + code 唯一性校验；</li>
 *   <li>更新：name/状态等（code 不可变）；</li>
 *   <li>删除：通过 {@link DictReferenceChecker} 校验引用关系，存在引用则拒绝（R12.4）。</li>
 * </ul>
 *
 * <p>本服务以组件扫描方式自注册（{@code @Service}），不在共享配置类中装配，避免并发冲突。
 */
@Service
public class DictAppService {

    private final RiskTypeRepository riskTypeRepository;
    private final RiskLevelRepository riskLevelRepository;
    private final DecisionTagRepository decisionTagRepository;
    private final DictReferenceChecker referenceChecker;

    public DictAppService(RiskTypeRepository riskTypeRepository,
                          RiskLevelRepository riskLevelRepository,
                          DecisionTagRepository decisionTagRepository,
                          DictReferenceChecker referenceChecker) {
        this.riskTypeRepository = riskTypeRepository;
        this.riskLevelRepository = riskLevelRepository;
        this.decisionTagRepository = decisionTagRepository;
        this.referenceChecker = referenceChecker;
    }

    // ============================ 风险类型 ============================

    /** 创建风险类型（R12.1）。 */
    public RiskType createRiskType(String code, String name) {
        RiskType t = RiskType.create(code, name);
        if (riskTypeRepository.existsByCode(code)) {
            throw BizException.duplicate("风险类型 code 已存在: " + code);
        }
        return riskTypeRepository.save(t);
    }

    /** 更新风险类型（R12.1）。 */
    public RiskType updateRiskType(Long id, String name, String status) {
        RiskType t = riskTypeRepository.findById(id)
                .orElseThrow(() -> BizException.notFound("风险类型不存在: id=" + id));
        t.update(name, parseStatus(status));
        riskTypeRepository.update(t);
        return t;
    }

    /** 列表查询风险类型（R12.1）。 */
    public List<RiskType> listRiskTypes() {
        return riskTypeRepository.findAll();
    }

    /** 删除风险类型，删除前校验引用关系（R12.4）。 */
    public void deleteRiskType(Long id) {
        RiskType t = riskTypeRepository.findById(id)
                .orElseThrow(() -> BizException.notFound("风险类型不存在: id=" + id));
        if (referenceChecker.isRiskTypeReferenced(t.getCode())) {
            throw BizException.invalidState("风险类型已被引用，无法删除: " + t.getCode());
        }
        riskTypeRepository.deleteById(id);
    }

    // ============================ 风险等级 ============================

    /** 创建风险等级（R12.1）。 */
    public RiskLevel createRiskLevel(String code, String name, int orderNo) {
        RiskLevel t = RiskLevel.create(code, name, orderNo);
        if (riskLevelRepository.existsByCode(code)) {
            throw BizException.duplicate("风险等级 code 已存在: " + code);
        }
        return riskLevelRepository.save(t);
    }

    /** 更新风险等级（R12.1）。 */
    public RiskLevel updateRiskLevel(Long id, String name, int orderNo, String status) {
        RiskLevel t = riskLevelRepository.findById(id)
                .orElseThrow(() -> BizException.notFound("风险等级不存在: id=" + id));
        t.update(name, orderNo, parseStatus(status));
        riskLevelRepository.update(t);
        return t;
    }

    /** 列表查询风险等级（按 order_no 升序，R12.1）。 */
    public List<RiskLevel> listRiskLevels() {
        return riskLevelRepository.findAll();
    }

    /** 删除风险等级，删除前校验引用关系（R12.4）。 */
    public void deleteRiskLevel(Long id) {
        RiskLevel t = riskLevelRepository.findById(id)
                .orElseThrow(() -> BizException.notFound("风险等级不存在: id=" + id));
        if (referenceChecker.isRiskLevelReferenced(t.getCode())) {
            throw BizException.invalidState("风险等级已被引用，无法删除: " + t.getCode());
        }
        riskLevelRepository.deleteById(id);
    }

    // ============================ 决策标签 ============================

    /** 创建决策标签（R12.1）。 */
    public DecisionTag createDecisionTag(String code, String name, String applicableAssetType) {
        DecisionTag t = DecisionTag.create(code, name, applicableAssetType);
        if (decisionTagRepository.existsByCode(code)) {
            throw BizException.duplicate("决策标签 code 已存在: " + code);
        }
        return decisionTagRepository.save(t);
    }

    /** 更新决策标签（R12.1）。 */
    public DecisionTag updateDecisionTag(Long id, String name, String applicableAssetType, String status) {
        DecisionTag t = decisionTagRepository.findById(id)
                .orElseThrow(() -> BizException.notFound("决策标签不存在: id=" + id));
        t.update(name, applicableAssetType, parseStatus(status));
        decisionTagRepository.update(t);
        return t;
    }

    /** 列表查询决策标签（R12.1）。 */
    public List<DecisionTag> listDecisionTags() {
        return decisionTagRepository.findAll();
    }

    /** 删除决策标签，删除前校验引用关系（R12.4）。 */
    public void deleteDecisionTag(Long id) {
        DecisionTag t = decisionTagRepository.findById(id)
                .orElseThrow(() -> BizException.notFound("决策标签不存在: id=" + id));
        if (referenceChecker.isDecisionTagReferenced(t.getCode())) {
            throw BizException.invalidState("决策标签已被引用，无法删除: " + t.getCode());
        }
        decisionTagRepository.deleteById(id);
    }

    /** 解析状态字符串为枚举；为空时返回 null（表示不变更状态）。 */
    private DictStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return DictStatus.valueOf(status);
        } catch (IllegalArgumentException ex) {
            throw BizException.invalidState("非法状态值: " + status);
        }
    }
}
