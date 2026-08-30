package com.riskplatform.ruleconfig.adapter.org;

import com.riskplatform.ruleconfig.application.org.OrgAppService;
import com.riskplatform.ruleconfig.domain.org.Org;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 机构 REST 适配器（R11.1/R11.2）。
 *
 * <p>端点：
 * <ul>
 *   <li>POST /api/v1/orgs 创建机构（自动计算物化路径 path）</li>
 *   <li>PUT  /api/v1/orgs/{id} 更新机构名称</li>
 *   <li>PUT  /api/v1/orgs/{id}/status 启用/禁用</li>
 *   <li>GET  /api/v1/orgs 列表，view=tree（默认）返回树形、view=list 返回扁平列表</li>
 *   <li>GET  /api/v1/orgs/{id} 单个机构</li>
 *   <li>GET  /api/v1/orgs/{id}/subtree 机构含下级范围内的全部机构（path 前缀）</li>
 *   <li>GET  /api/v1/orgs/{id}/applicable?targetId=&includeSub= 含下级判定工具</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/orgs")
public class OrgController {

    private final OrgAppService appService;

    public OrgController(OrgAppService appService) {
        this.appService = appService;
    }

    @PostMapping
    public OrgView create(@Valid @RequestBody CreateOrgRequest req) {
        return OrgView.from(appService.create(req.code(), req.name(), req.parentId()));
    }

    @PutMapping("/{id}")
    public OrgView rename(@PathVariable Long id, @Valid @RequestBody UpdateOrgRequest req) {
        return OrgView.from(appService.rename(id, req.name()));
    }

    @PutMapping("/{id}/status")
    public OrgView setStatus(@PathVariable Long id, @RequestParam boolean enabled) {
        return OrgView.from(appService.setStatus(id, enabled));
    }

    @GetMapping("/{id}")
    public OrgView get(@PathVariable Long id) {
        return OrgView.from(appService.get(id));
    }

    /**
     * 机构列表。view=tree（默认）返回树形结构，view=list 返回扁平列表。
     */
    @GetMapping
    public Object list(@RequestParam(defaultValue = "tree") String view) {
        List<Org> all = appService.list();
        if ("list".equalsIgnoreCase(view)) {
            return all.stream().map(OrgView::from).toList();
        }
        return buildTree(all);
    }

    /** 机构含下级范围内的全部机构（R11.2/R11.4）。 */
    @GetMapping("/{id}/subtree")
    public List<OrgView> subtree(@PathVariable Long id) {
        return appService.listWithinSubtree(id).stream().map(OrgView::from).toList();
    }

    /** 含下级判定工具：机构 {id} 是否落入 targetId 的适用范围（R11.2/R11.4）。 */
    @GetMapping("/{id}/applicable")
    public ApplicableView applicable(@PathVariable Long id,
                                     @RequestParam Long targetId,
                                     @RequestParam(defaultValue = "true") boolean includeSub) {
        boolean applicable = appService.isApplicable(id, targetId, includeSub);
        return new ApplicableView(id, targetId, includeSub, applicable);
    }

    /** 将扁平机构列表装配为树形（按 parentId 关联，根为 parentId=null）。 */
    private List<OrgNodeView> buildTree(List<Org> all) {
        Map<Long, OrgNodeView> index = new LinkedHashMap<>();
        for (Org o : all) {
            index.put(o.getId(), OrgNodeView.from(o));
        }
        List<OrgNodeView> roots = new ArrayList<>();
        for (Org o : all) {
            OrgNodeView node = index.get(o.getId());
            if (o.getParentId() == null) {
                roots.add(node);
            } else {
                OrgNodeView parent = index.get(o.getParentId());
                if (parent != null) {
                    parent.children().add(node);
                } else {
                    // 父不存在（脏数据）时按根处理，保证不丢节点
                    roots.add(node);
                }
            }
        }
        return roots;
    }

    /** 创建请求。基础非空校验由 Bean Validation 完成，长度/字符集校验在领域层。 */
    public record CreateOrgRequest(@NotBlank String code, @NotBlank String name, Long parentId) {
    }

    /** 更新请求（仅名称）。 */
    public record UpdateOrgRequest(@NotBlank String name) {
    }

    /** 扁平视图对象。 */
    public record OrgView(Long id, String code, String name, Long parentId, String path, String status) {
        static OrgView from(Org o) {
            return new OrgView(o.getId(), o.getCode(), o.getName(), o.getParentId(), o.getPath(), o.getStatus().name());
        }
    }

    /** 树形节点视图对象（含子节点）。 */
    public record OrgNodeView(Long id, String code, String name, Long parentId, String path, String status,
                              List<OrgNodeView> children) {
        static OrgNodeView from(Org o) {
            return new OrgNodeView(o.getId(), o.getCode(), o.getName(), o.getParentId(), o.getPath(),
                    o.getStatus().name(), new ArrayList<>());
        }
    }

    /** 含下级判定结果视图。 */
    public record ApplicableView(Long orgId, Long targetId, boolean includeSub, boolean applicable) {
    }
}
