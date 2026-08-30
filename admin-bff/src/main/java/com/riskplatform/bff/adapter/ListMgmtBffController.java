package com.riskplatform.bff.adapter;

import com.riskplatform.bff.application.BffAggregationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static com.riskplatform.bff.adapter.RuleConfigBffController.buildQuery;

/**
 * 名单管理 BFF 聚合接口：转发至 screening-service。
 *
 * <p>名单库 / 维度 / 附加属性 / 库内记录，不绑定黑/白名单类型。
 */
@RestController
@RequestMapping("/bff/api/v1")
public class ListMgmtBffController {

    private final BffAggregationService aggregation;

    public ListMgmtBffController(BffAggregationService aggregation) {
        this.aggregation = aggregation;
    }

    private static String auth(HttpServletRequest request) {
        return request.getHeader(HttpHeaders.AUTHORIZATION);
    }

    // —— 名单维度 ——

    @GetMapping("/list-dimensions")
    public Object listDimensions(@RequestParam Map<String, String> params, HttpServletRequest request) {
        return aggregation.screeningGet("/api/v1/list-dimensions" + buildQuery(params), auth(request));
    }

    @PostMapping("/list-dimensions")
    public Object createDimension(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        return aggregation.screeningPost("/api/v1/list-dimensions", body, auth(request));
    }

    @PutMapping("/list-dimensions/{id}")
    public Object updateDimension(@PathVariable Long id, @RequestBody Map<String, Object> body,
                                  HttpServletRequest request) {
        return aggregation.screeningPut("/api/v1/list-dimensions/" + id, body, auth(request));
    }

    @PostMapping("/list-dimensions/batch-delete")
    public Object batchDeleteDimensions(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        return aggregation.screeningPost("/api/v1/list-dimensions/batch-delete", body, auth(request));
    }

    // —— 名单附加属性 ——

    @GetMapping("/list-attr-defs")
    public Object listAttrDefs(@RequestParam Map<String, String> params, HttpServletRequest request) {
        return aggregation.screeningGet("/api/v1/list-attr-defs" + buildQuery(params), auth(request));
    }

    @PostMapping("/list-attr-defs")
    public Object createAttrDef(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        return aggregation.screeningPost("/api/v1/list-attr-defs", body, auth(request));
    }

    @PutMapping("/list-attr-defs/{id}")
    public Object updateAttrDef(@PathVariable Long id, @RequestBody Map<String, Object> body,
                                HttpServletRequest request) {
        return aggregation.screeningPut("/api/v1/list-attr-defs/" + id, body, auth(request));
    }

    @PostMapping("/list-attr-defs/batch-delete")
    public Object batchDeleteAttrDefs(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        return aggregation.screeningPost("/api/v1/list-attr-defs/batch-delete", body, auth(request));
    }

    // —— 名单库 ——

    @GetMapping("/list-libraries")
    public Object listLibraries(@RequestParam Map<String, String> params, HttpServletRequest request) {
        return aggregation.screeningGet("/api/v1/list-libraries" + buildQuery(params), auth(request));
    }

    @PostMapping("/list-libraries")
    public Object createLibrary(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        return aggregation.screeningPost("/api/v1/list-libraries", body, auth(request));
    }

    @PutMapping("/list-libraries/{id}")
    public Object updateLibrary(@PathVariable Long id, @RequestBody Map<String, Object> body,
                                HttpServletRequest request) {
        return aggregation.screeningPut("/api/v1/list-libraries/" + id, body, auth(request));
    }

    @DeleteMapping("/list-libraries/{id}")
    public Object deleteLibrary(@PathVariable Long id, HttpServletRequest request) {
        return aggregation.screeningDelete("/api/v1/list-libraries/" + id, auth(request));
    }

    @GetMapping("/list-libraries/{id}/references")
    public Object libraryReferences(@PathVariable Long id, HttpServletRequest request) {
        return aggregation.screeningGet("/api/v1/list-libraries/" + id + "/references", auth(request));
    }

    @GetMapping("/list-libraries/{id}/stats")
    public Object libraryStats(@PathVariable Long id, HttpServletRequest request) {
        return aggregation.screeningGet("/api/v1/list-libraries/" + id + "/stats", auth(request));
    }

    @PostMapping("/list-libraries/{id}/sync")
    public Object syncLibrary(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body,
                              HttpServletRequest request) {
        return aggregation.screeningPost("/api/v1/list-libraries/" + id + "/sync",
                body == null ? Map.of() : body, auth(request));
    }

    @GetMapping("/list-libraries/{id}/import-audits")
    public Object importAudits(@PathVariable Long id, @RequestParam Map<String, String> params,
                               HttpServletRequest request) {
        return aggregation.screeningGet("/api/v1/list-libraries/" + id + "/import-audits" + buildQuery(params),
                auth(request));
    }

    // —— 库内记录 ——

    @GetMapping("/list-entries")
    public Object listEntries(@RequestParam Map<String, String> params, HttpServletRequest request) {
        return aggregation.screeningGet("/api/v1/list-entries" + buildQuery(params), auth(request));
    }

    @GetMapping("/list-entries/check")
    public Object checkEntries(@RequestParam Map<String, String> params, HttpServletRequest request) {
        return aggregation.screeningGet("/api/v1/list-entries/check" + buildQuery(params), auth(request));
    }

    @PostMapping("/list-entries")
    public Object createEntry(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        return aggregation.screeningPost("/api/v1/list-entries", body, auth(request));
    }

    @PutMapping("/list-entries/{id}")
    public Object updateEntry(@PathVariable Long id, @RequestBody Map<String, Object> body,
                              HttpServletRequest request) {
        return aggregation.screeningPut("/api/v1/list-entries/" + id, body, auth(request));
    }

    @PostMapping("/list-entries/batch-delete")
    public Object batchDeleteEntries(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        return aggregation.screeningPost("/api/v1/list-entries/batch-delete", body, auth(request));
    }

    @PostMapping("/list-entries/batch-enabled")
    public Object batchEnabledEntries(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        return aggregation.screeningPost("/api/v1/list-entries/batch-enabled", body, auth(request));
    }

    // —— 黑白名单记录（screening-service /api/v1/lists） ——

    @GetMapping("/lists")
    public Object listRecords(@RequestParam Map<String, String> params, HttpServletRequest request) {
        return aggregation.screeningGet("/api/v1/lists" + buildQuery(params), auth(request));
    }

    @GetMapping("/lists/check")
    public Object checkLists(@RequestParam Map<String, String> params, HttpServletRequest request) {
        return aggregation.screeningGet("/api/v1/lists/check" + buildQuery(params), auth(request));
    }

    @PostMapping("/lists")
    public Object createRecord(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        return aggregation.screeningPost("/api/v1/lists", body, auth(request));
    }

    @PutMapping("/lists/{id}")
    public Object updateRecord(@PathVariable Long id, @RequestBody Map<String, Object> body,
                               HttpServletRequest request) {
        return aggregation.screeningPut("/api/v1/lists/" + id, body, auth(request));
    }

    @DeleteMapping("/lists/{id}")
    public Object deleteRecord(@PathVariable Long id, HttpServletRequest request) {
        return aggregation.screeningDelete("/api/v1/lists/" + id, auth(request));
    }
}
