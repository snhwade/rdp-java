package com.riskplatform.screening.adapter.list;

import com.riskplatform.screening.application.list.ListManagementService;
import com.riskplatform.screening.domain.list.ListRecord;
import com.riskplatform.screening.domain.list.ListType;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 名单记录管理 REST 适配器（S1）。
 *
 * <ul>
 *   <li>POST   /api/v1/lists          新建名单记录</li>
 *   <li>GET    /api/v1/lists?type=    列出名单记录（可按类型过滤）</li>
 *   <li>PUT    /api/v1/lists/{id}     更新名单记录</li>
 *   <li>DELETE /api/v1/lists/{id}     删除名单记录</li>
 *   <li>GET    /api/v1/lists/check    黑/白名单命中判定（供网关编排）</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/lists")
public class ListRecordController {

    private final ListManagementService service;

    public ListRecordController(ListManagementService service) {
        this.service = service;
    }

    @PostMapping
    public ListView create(@RequestBody CreateListRequest req) {
        LocalDateTime expireAt = parseExpire(req.expireAt());
        ListRecord r = service.create(ListType.valueOf(req.listType()), req.dimension(),
                req.dimensionValue(), req.reason(), req.immuneRuleId(), expireAt);
        return ListView.from(r);
    }

    @GetMapping
    public List<ListView> list(@RequestParam(name = "type", required = false) String type) {
        ListType listType = (type == null || type.isBlank()) ? null : ListType.valueOf(type);
        return service.list(listType).stream().map(ListView::from).toList();
    }

    @PutMapping("/{id}")
    public ListView update(@PathVariable("id") Long id, @RequestBody UpdateListRequest req) {
        ListRecord r = service.update(id, req.dimensionValue(), req.reason(),
                req.immuneRuleId(), parseExpire(req.expireAt()), req.enabled());
        return ListView.from(r);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable("id") Long id) {
        service.delete(id);
    }

    /** 黑/白名单命中判定：返回该维度值命中的黑/白名单记录。 */
    @GetMapping("/check")
    public CheckView check(@RequestParam("dimension") String dimension,
                           @RequestParam("value") String value) {
        List<ListRecord> black = service.matchBlack(dimension, value);
        List<ListRecord> white = service.matchWhite(dimension, value);
        List<ListRecord> watch = service.matchWatch(dimension, value);
        return new CheckView(
                !black.isEmpty(),
                black.stream().map(ListView::from).toList(),
                !white.isEmpty(),
                white.stream().map(ListView::from).toList(),
                !watch.isEmpty(),
                watch.stream().map(ListView::from).toList());
    }

    private static LocalDateTime parseExpire(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        return LocalDateTime.parse(s, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    public record CreateListRequest(
            @NotBlank String listType,
            @NotBlank String dimension,
            @NotBlank String dimensionValue,
            String reason,
            Long immuneRuleId,
            String expireAt) {
    }

    public record UpdateListRequest(
            String dimensionValue,
            String reason,
            Long immuneRuleId,
            String expireAt,
            boolean enabled) {
    }

    public record ListView(Long id, String listType, String dimension, String dimensionValue,
                           String reason, Long immuneRuleId, String expireAt, boolean enabled) {
        static ListView from(ListRecord r) {
            return new ListView(r.id(), r.listType().name(), r.dimension(), r.dimensionValue(),
                    r.reason(), r.immuneRuleId(),
                    r.expireAt() == null ? null : r.expireAt().toString(), r.enabled());
        }
    }

    public record CheckView(boolean blackHit, List<ListView> blackRecords,
                            boolean whiteHit, List<ListView> whiteRecords,
                            boolean watchHit, List<ListView> watchRecords) {
    }
}
