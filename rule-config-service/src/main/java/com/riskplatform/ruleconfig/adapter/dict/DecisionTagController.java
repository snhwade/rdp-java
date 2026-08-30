package com.riskplatform.ruleconfig.adapter.dict;

import com.riskplatform.ruleconfig.application.dict.DictAppService;
import com.riskplatform.ruleconfig.domain.dict.DecisionTag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 决策标签字典 REST 适配器（R12.1/R12.4）。
 *
 * <ul>
 *   <li>POST   /api/v1/decision-tags      创建</li>
 *   <li>GET    /api/v1/decision-tags      列表</li>
 *   <li>PUT    /api/v1/decision-tags/{id} 更新</li>
 *   <li>DELETE /api/v1/decision-tags/{id} 删除（引用校验）</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/decision-tags")
public class DecisionTagController {

    private final DictAppService appService;

    public DecisionTagController(DictAppService appService) {
        this.appService = appService;
    }

    @PostMapping
    public DecisionTagView create(@Valid @RequestBody CreateRequest req) {
        return DecisionTagView.from(appService.createDecisionTag(req.code(), req.name(), req.applicableAssetType()));
    }

    @GetMapping
    public List<DecisionTagView> list() {
        return appService.listDecisionTags().stream().map(DecisionTagView::from).toList();
    }

    @PutMapping("/{id}")
    public DecisionTagView update(@PathVariable Long id, @Valid @RequestBody UpdateRequest req) {
        return DecisionTagView.from(appService.updateDecisionTag(id, req.name(),
                req.applicableAssetType(), req.status()));
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        appService.deleteDecisionTag(id);
    }

    /** 创建请求。 */
    public record CreateRequest(@NotBlank String code, @NotBlank String name, String applicableAssetType) {
    }

    /** 更新请求。 */
    public record UpdateRequest(@NotBlank String name, String applicableAssetType, String status) {
    }

    /** 视图对象。 */
    public record DecisionTagView(Long id, String code, String name, String applicableAssetType, String status) {
        static DecisionTagView from(DecisionTag e) {
            return new DecisionTagView(e.getId(), e.getCode(), e.getName(),
                    e.getApplicableAssetType(), e.getStatus().name());
        }
    }
}
