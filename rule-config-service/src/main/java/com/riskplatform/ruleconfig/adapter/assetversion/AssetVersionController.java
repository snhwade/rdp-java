package com.riskplatform.ruleconfig.adapter.assetversion;

import com.riskplatform.ruleconfig.application.assetversion.AssetVersionService;
import com.riskplatform.ruleconfig.domain.assetversion.AssetVersion;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 资产版本快照 REST 适配器（S6）。
 *
 * <ul>
 *   <li>POST /api/v1/asset-versions                生成版本快照</li>
 *   <li>GET  /api/v1/asset-versions?assetType=&assetId= 历史版本（倒序）</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/asset-versions")
public class AssetVersionController {

    private final AssetVersionService service;

    public AssetVersionController(AssetVersionService service) {
        this.service = service;
    }

    @PostMapping
    public AssetVersion snapshot(@RequestBody SnapshotRequest req) {
        return service.snapshot(req.assetType(), req.assetId(), req.content(), req.operator());
    }

    @GetMapping
    public List<AssetVersion> list(@RequestParam("assetType") String assetType,
                                   @RequestParam("assetId") String assetId) {
        return service.listVersions(assetType, assetId);
    }

    public record SnapshotRequest(
            @NotBlank String assetType,
            @NotBlank String assetId,
            String content,
            String operator) {
    }
}
