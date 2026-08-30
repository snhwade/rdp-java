package com.riskplatform.ruleconfig.adapter.ratingmodel;

import com.riskplatform.ruleconfig.application.ratingmodel.RatingModelAppService;
import com.riskplatform.ruleconfig.application.ratingmodel.RatingModelVersionAppService;
import com.riskplatform.ruleconfig.domain.ratingmodel.RatingModel;
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

import java.math.BigDecimal;
import java.util.List;

/**
 * 评级模型 REST 适配器（risk-console-redesign，R10）。
 *
 * <p>命名中性化：路径与标识不含任何产品厂商专有名词。
 *
 * <p>端点：
 * <ul>
 *   <li>GET    /api/v1/rating-models?eventTypeCode=&executionMode=&subject= 卡片墙（R10.1）</li>
 *   <li>POST   /api/v1/rating-models                创建（执行方式/主体枚举校验，R10.2/R10.3）</li>
 *   <li>GET    /api/v1/rating-models/{id}           详情三页签数据（R10.4/R10.5）</li>
 *   <li>PUT    /api/v1/rating-models/{id}           保存→新建版本（R10.6）</li>
 *   <li>POST   /api/v1/rating-models/{id}:online    上线（R10.7）</li>
 *   <li>POST   /api/v1/rating-models/{id}:offline   下线（R10.7）</li>
 * </ul>
 *
 * <p>执行方式与评级主体的取值合法性由解析入参为枚举时保证：非法字符串经
 * {@link #parseExecutionMode}/{@link #parseSubject} 抛字段级校验错误（R10.3）。
 */
@RestController
@RequestMapping("/api/v1/rating-models")
public class RatingModelController {

    private final RatingModelAppService appService;
    private final RatingModelVersionAppService versionAppService;

    public RatingModelController(RatingModelAppService appService,
                                 RatingModelVersionAppService versionAppService) {
        this.appService = appService;
        this.versionAppService = versionAppService;
    }

    /**
     * 卡片墙列表（R10.1）：顶部可选所属事件 / 执行方式 / 评级主体筛选。
     * 事件筛选下推到仓储；执行方式与评级主体在应用边界做内存过滤。
     */
    @GetMapping
    public List<RatingModelView> list(
            @RequestParam(name = "eventTypeCode", required = false) String eventTypeCode,
            @RequestParam(name = "executionMode", required = false) String executionMode,
            @RequestParam(name = "subject", required = false) String subject) {
        RatingModel.ExecutionMode mode = parseExecutionMode(executionMode, true);
        RatingModel.Subject subj = parseSubject(subject, true);
        return appService.list(eventTypeCode).stream()
                .filter(m -> mode == null || m.getExecutionMode() == mode)
                .filter(m -> subj == null || m.getSubject() == subj)
                .map(RatingModelView::summary)
                .toList();
    }

    /** 创建评级模型（R10.2/R10.3）。 */
    @PostMapping
    public RatingModelView create(@Valid @RequestBody CreateRequest req) {
        RatingModel created = appService.create(req.name(), req.eventTypeCode(),
                parseExecutionMode(req.executionMode(), false),
                parseSubject(req.subject(), false),
                parseGradingMode(req.gradingMode(), false));
        return RatingModelView.detail(created);
    }

    /** 详情三页签数据（R10.4/R10.5）：评级模型配置 + 源码（当前版本快照 JSON）+ 版本历史。 */
    @GetMapping("/{id}")
    public RatingModelDetailView get(@PathVariable("id") Long id) {
        RatingModel model = appService.get(id);
        RatingModelVersionAppService.VersionSnapshot current =
                versionAppService.getSnapshot(id, model.getVersion());
        List<RatingModelVersionAppService.VersionSummary> versions = versionAppService.listVersions(id);
        return new RatingModelDetailView(RatingModelView.detail(model), current.sourceJson(), versions);
    }

    /** 保存→新建版本（R10.6）：更新名称/定级方式/等级区间/子项。 */
    @PutMapping("/{id}")
    public RatingModelView save(@PathVariable("id") Long id, @RequestBody SaveRequest req) {
        RatingModel saved = appService.save(id, req.name(),
                parseGradingMode(req.gradingMode(), true),
                toGradeBands(req.gradeBands()), toItems(req.items()));
        return RatingModelView.detail(saved);
    }

    /** 上线（R10.7）。 */
    @PostMapping("/{id}:online")
    public RatingModelView online(@PathVariable("id") Long id) {
        return RatingModelView.detail(appService.online(id));
    }

    /** 下线（R10.7）。 */
    @PostMapping("/{id}:offline")
    public RatingModelView offline(@PathVariable("id") Long id) {
        return RatingModelView.detail(appService.offline(id));
    }

    // —— 解析辅助 ——

    private RatingModel.ExecutionMode parseExecutionMode(String raw, boolean nullable) {
        if (raw == null || raw.isBlank()) {
            if (nullable) {
                return null;
            }
            throw com.riskplatform.common.error.ValidationException.builder()
                    .field("executionMode", "必填，取值须为 REALTIME/SCHEDULED 之一").build();
        }
        try {
            return RatingModel.ExecutionMode.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw com.riskplatform.common.error.ValidationException.builder()
                    .field("executionMode", "非法取值，须为 REALTIME/SCHEDULED 之一: " + raw).build();
        }
    }

    private RatingModel.Subject parseSubject(String raw, boolean nullable) {
        if (raw == null || raw.isBlank()) {
            if (nullable) {
                return null;
            }
            throw com.riskplatform.common.error.ValidationException.builder()
                    .field("subject", "必填，取值须为 MERCHANT/INDIVIDUAL 之一").build();
        }
        try {
            return RatingModel.Subject.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw com.riskplatform.common.error.ValidationException.builder()
                    .field("subject", "非法取值，须为 MERCHANT/INDIVIDUAL 之一: " + raw).build();
        }
    }

    private RatingModel.GradingMode parseGradingMode(String raw, boolean nullable) {
        if (raw == null || raw.isBlank()) {
            if (nullable) {
                return null;
            }
            throw com.riskplatform.common.error.ValidationException.builder()
                    .field("gradingMode", "必填，取值须为 SCORE_BASED/DIRECT/MIXED 之一").build();
        }
        try {
            return RatingModel.GradingMode.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw com.riskplatform.common.error.ValidationException.builder()
                    .field("gradingMode", "非法取值，须为 SCORE_BASED/DIRECT/MIXED 之一: " + raw).build();
        }
    }

    private List<RatingModel.GradeBand> toGradeBands(List<GradeBandRequest> raw) {
        if (raw == null) {
            return null;
        }
        return raw.stream()
                .map(b -> new RatingModel.GradeBand(b.minScore(), b.maxScore(), b.grade(),
                        b.orderNo() == null ? 0 : b.orderNo()))
                .toList();
    }

    private List<RatingModel.RatingItem> toItems(List<RatingItemRequest> raw) {
        if (raw == null) {
            return null;
        }
        return raw.stream()
                .map(i -> new RatingModel.RatingItem(i.category(), i.subItem(), i.condition(),
                        i.score(), i.subItemCap(), i.importance(), i.grade()))
                .toList();
    }

    // —— 请求/视图对象 ——

    /** 创建评级模型请求。基础非空由 Bean Validation 完成，枚举合法性在解析阶段校验。 */
    public record CreateRequest(@NotBlank String name, @NotBlank String eventTypeCode,
                                String executionMode, String subject, String gradingMode) {
    }

    /** 保存请求（保存→新建版本）：可更新名称、定级方式、等级区间与子项。 */
    public record SaveRequest(String name, String gradingMode,
                              List<GradeBandRequest> gradeBands, List<RatingItemRequest> items) {
    }

    /** 等级区间请求。 */
    public record GradeBandRequest(BigDecimal minScore, BigDecimal maxScore, String grade, Integer orderNo) {
    }

    /** 评级子项/定级项请求。 */
    public record RatingItemRequest(String category, String subItem, String condition,
                                    BigDecimal score, BigDecimal subItemCap, String importance, String grade) {
    }

    /** 评级模型视图对象（卡片墙摘要 / 详情通用）。 */
    public record RatingModelView(Long id, String name, String eventTypeCode, String executionMode,
                                  String subject, String gradingMode, String status, int version,
                                  List<RatingModel.GradeBand> gradeBands, List<RatingModel.RatingItem> items) {

        static RatingModelView summary(RatingModel m) {
            return new RatingModelView(m.getId(), m.getName(), m.getEventTypeCode(),
                    m.getExecutionMode() == null ? null : m.getExecutionMode().name(),
                    m.getSubject() == null ? null : m.getSubject().name(),
                    m.getGradingMode() == null ? null : m.getGradingMode().name(),
                    m.getStatus(), m.getVersion(), null, null);
        }

        static RatingModelView detail(RatingModel m) {
            return new RatingModelView(m.getId(), m.getName(), m.getEventTypeCode(),
                    m.getExecutionMode() == null ? null : m.getExecutionMode().name(),
                    m.getSubject() == null ? null : m.getSubject().name(),
                    m.getGradingMode() == null ? null : m.getGradingMode().name(),
                    m.getStatus(), m.getVersion(), m.getGradeBands(), m.getItems());
        }
    }

    /**
     * 详情三页签数据（R10.4/R10.5）：
     * <ul>
     *   <li>{@code model} —— 「评级模型」页签：配置（含等级区间与子项）</li>
     *   <li>{@code sourceJson} —— 「源码」页签：当前版本配置 JSON 快照</li>
     *   <li>{@code versions} —— 「版本历史」页签：全部版本元数据</li>
     * </ul>
     */
    public record RatingModelDetailView(RatingModelView model, String sourceJson,
                                        List<RatingModelVersionAppService.VersionSummary> versions) {
    }
}
