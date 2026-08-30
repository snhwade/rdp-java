package com.riskplatform.ruleconfig.adapter.dict;

import com.riskplatform.ruleconfig.application.dict.EnumLibAppService;
import com.riskplatform.ruleconfig.domain.enums.EnumLib;
import com.riskplatform.ruleconfig.domain.enums.EnumValue;
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
 * 枚举库与枚举值 REST 适配器（R12.2/R12.3/R12.4）。
 *
 * <p>枚举库：
 * <ul>
 *   <li>POST   /api/v1/enum-libs              创建</li>
 *   <li>GET    /api/v1/enum-libs              列表</li>
 *   <li>GET    /api/v1/enum-libs/{id}         详情</li>
 *   <li>PUT    /api/v1/enum-libs/{id}         更新</li>
 *   <li>DELETE /api/v1/enum-libs/{id}         删除（引用校验 + 级联删除枚举值）</li>
 * </ul>
 *
 * <p>枚举值：
 * <ul>
 *   <li>GET    /api/v1/enum-libs/{id}/values            列出</li>
 *   <li>POST   /api/v1/enum-libs/{id}/values            新增</li>
 *   <li>PUT    /api/v1/enum-libs/{id}/values/{valueId}  更新</li>
 *   <li>DELETE /api/v1/enum-libs/{id}/values/{valueId}  删除（引用校验）</li>
 *   <li>POST   /api/v1/enum-libs/{id}/values/import     批量导入（upsert）</li>
 *   <li>GET    /api/v1/enum-libs/{id}/values/export     导出</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/enum-libs")
public class EnumLibController {

    private final EnumLibAppService appService;

    public EnumLibController(EnumLibAppService appService) {
        this.appService = appService;
    }

    // ----------------------------- 枚举库 -----------------------------

    @PostMapping
    public EnumLibView create(@Valid @RequestBody CreateLibRequest req) {
        return EnumLibView.from(appService.createLib(req.code(), req.name(), req.dataType()));
    }

    @GetMapping
    public List<EnumLibView> list() {
        return appService.listLibs().stream().map(EnumLibView::from).toList();
    }

    @GetMapping("/{id}")
    public EnumLibView get(@PathVariable Long id) {
        return EnumLibView.from(appService.getLib(id));
    }

    @PutMapping("/{id}")
    public EnumLibView update(@PathVariable Long id, @Valid @RequestBody UpdateLibRequest req) {
        return EnumLibView.from(appService.updateLib(id, req.name(), req.dataType(), req.status()));
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        appService.deleteLib(id);
    }

    // ----------------------------- 枚举值 -----------------------------

    @GetMapping("/{id}/values")
    public List<EnumValueView> listValues(@PathVariable Long id) {
        return appService.listValues(id).stream().map(EnumValueView::from).toList();
    }

    @PostMapping("/{id}/values")
    public EnumValueView addValue(@PathVariable Long id, @Valid @RequestBody AddValueRequest req) {
        return EnumValueView.from(appService.addValue(id, req.value(), req.label(), nz(req.orderNo())));
    }

    @PutMapping("/{id}/values/{valueId}")
    public EnumValueView updateValue(@PathVariable Long id, @PathVariable Long valueId,
                                     @RequestBody UpdateValueRequest req) {
        return EnumValueView.from(appService.updateValue(valueId, req.label(), nz(req.orderNo())));
    }

    @DeleteMapping("/{id}/values/{valueId}")
    public void deleteValue(@PathVariable Long id, @PathVariable Long valueId) {
        appService.deleteValue(valueId);
    }

    @PostMapping("/{id}/values/import")
    public List<EnumValueView> importValues(@PathVariable Long id, @RequestBody List<ImportValueItem> items) {
        List<EnumLibAppService.ImportItem> mapped = items == null ? List.of()
                : items.stream()
                .map(i -> new EnumLibAppService.ImportItem(i.value(), i.label(), nz(i.orderNo())))
                .toList();
        return appService.importValues(id, mapped).stream().map(EnumValueView::from).toList();
    }

    @GetMapping("/{id}/values/export")
    public List<EnumValueView> exportValues(@PathVariable Long id) {
        return appService.exportValues(id).stream().map(EnumValueView::from).toList();
    }

    private static int nz(Integer v) {
        return v == null ? 0 : v;
    }

    // ----------------------------- 请求/视图 -----------------------------

    /** 创建枚举库请求。 */
    public record CreateLibRequest(@NotBlank String code, @NotBlank String name, @NotBlank String dataType) {
    }

    /** 更新枚举库请求。 */
    public record UpdateLibRequest(@NotBlank String name, String dataType, String status) {
    }

    /** 新增枚举值请求。 */
    public record AddValueRequest(@NotBlank String value, String label, Integer orderNo) {
    }

    /** 更新枚举值请求。 */
    public record UpdateValueRequest(String label, Integer orderNo) {
    }

    /** 导入项请求。 */
    public record ImportValueItem(String value, String label, Integer orderNo) {
    }

    /** 枚举库视图。 */
    public record EnumLibView(Long id, String code, String name, String dataType, String status) {
        static EnumLibView from(EnumLib e) {
            return new EnumLibView(e.getId(), e.getCode(), e.getName(),
                    e.getDataType().name(), e.getStatus().name());
        }
    }

    /** 枚举值视图。 */
    public record EnumValueView(Long id, Long enumLibId, String value, String label, int orderNo) {
        static EnumValueView from(EnumValue e) {
            return new EnumValueView(e.getId(), e.getEnumLibId(), e.getValue(), e.getLabel(), e.getOrderNo());
        }
    }
}
