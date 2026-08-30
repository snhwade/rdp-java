package com.riskplatform.ruleconfig.application.ratingmodel;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.riskplatform.common.error.BizException;
import com.riskplatform.ruleconfig.application.permission.UserContextProvider;
import com.riskplatform.ruleconfig.domain.ratingmodel.RatingModel;
import com.riskplatform.ruleconfig.domain.ratingmodel.RatingModelVersion;
import com.riskplatform.ruleconfig.domain.ratingmodel.RatingModelVersionRepository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 评级模型版本应用服务（risk-console-redesign，R10.6/R11.5/R12.1/R13.1）。
 *
 * <p>职责：
 * <ul>
 *   <li><b>快照写入</b>：由 {@link RatingModelAppService} 在评级模型创建/保存成功后调用
 *       {@link #snapshot(RatingModel)}，把当次评级模型整体（基础属性 + 等级区间 + 评级子项/定级项）
 *       序列化为快照，以「该模型当前最大版本号 + 1」写入 {@code rating_model_version} 表
 *       （版本号从 1 起递增）。快照 JSON 即「源码」页签所展示的当前版本配置（R10.5）。</li>
 *   <li><b>版本列表</b>：{@link #listVersions(Long)} 按版本号降序返回历史版本元数据（R10.5）。</li>
 *   <li><b>单版本快照</b>：{@link #getSnapshot(Long, int)} 返回指定版本的完整快照内容。</li>
 * </ul>
 */
public class RatingModelVersionAppService {

    private final RatingModelVersionRepository versionRepository;
    private final ObjectMapper objectMapper;
    private final UserContextProvider userContextProvider;

    public RatingModelVersionAppService(RatingModelVersionRepository versionRepository,
                                        ObjectMapper objectMapper,
                                        UserContextProvider userContextProvider) {
        this.versionRepository = versionRepository;
        this.objectMapper = objectMapper;
        this.userContextProvider = userContextProvider;
    }

    /**
     * 为给定评级模型写入一条新版本快照（version = 当前最大版本号 + 1，从 1 起）。
     *
     * <p>在评级模型创建/保存成功后调用；createdBy 取自当前登录用户，缺省 anonymous。
     */
    public RatingModelVersion snapshot(RatingModel model) {
        if (model == null || model.getId() == null) {
            throw BizException.invalidState("评级模型尚未持久化，无法生成版本快照");
        }
        int nextVersion = versionRepository.findMaxVersion(model.getId()) + 1;
        String snapshotJson = writeJson(toSnapshotMap(model));
        RatingModelVersion version =
                new RatingModelVersion(model.getId(), nextVersion, snapshotJson, currentUsername());
        return versionRepository.save(version);
    }

    /** 版本列表（版本号降序），仅返回元数据视图，不含快照体（R10.5）。 */
    public List<VersionSummary> listVersions(Long ratingModelId) {
        return versionRepository.findByRatingModelId(ratingModelId).stream()
                .map(v -> new VersionSummary(v.getVersion(), v.getCreatedBy(), v.getCreatedAt()))
                .toList();
    }

    /** 查询指定版本的完整快照内容（含等级区间与定级配置）。 */
    public VersionSnapshot getSnapshot(Long ratingModelId, int version) {
        RatingModelVersion v = versionRepository
                .findByRatingModelIdAndVersion(ratingModelId, version)
                .orElseThrow(() -> BizException.notFound(
                        "评级模型版本不存在: modelId=" + ratingModelId + ", version=" + version));
        return new VersionSnapshot(v.getVersion(), v.getCreatedBy(), v.getCreatedAt(),
                readMap(v.getSnapshotJson()), v.getSnapshotJson());
    }

    /** 评级模型 → 快照 Map（基础属性 + 等级区间 + 评级子项/定级项）。 */
    private Map<String, Object> toSnapshotMap(RatingModel model) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", model.getName());
        map.put("eventTypeCode", model.getEventTypeCode());
        map.put("executionMode", model.getExecutionMode() == null ? null : model.getExecutionMode().name());
        map.put("subject", model.getSubject() == null ? null : model.getSubject().name());
        map.put("gradingMode", model.getGradingMode() == null ? null : model.getGradingMode().name());
        map.put("status", model.getStatus());
        map.put("version", model.getVersion());
        map.put("gradeBands", objectMapper.convertValue(model.getGradeBands(),
                new TypeReference<List<Object>>() {}));
        map.put("items", objectMapper.convertValue(model.getItems(),
                new TypeReference<List<Object>>() {}));
        return map;
    }

    private String currentUsername() {
        try {
            return userContextProvider.currentUser().username();
        } catch (Exception e) {
            return "anonymous";
        }
    }

    private String writeJson(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (Exception e) {
            throw new IllegalStateException("评级模型版本快照序列化失败: " + e.getMessage(), e);
        }
    }

    private Map<String, Object> readMap(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() {});
        } catch (Exception e) {
            throw new IllegalStateException("评级模型版本快照反序列化失败: " + e.getMessage(), e);
        }
    }

    /** 版本列表项（元数据视图，含版本号，R10.5）。 */
    public record VersionSummary(int version, String createdBy, java.time.LocalDateTime createdAt) {
    }

    /** 单个版本的完整快照（含版本元数据 + 快照内容 Map + 原始 JSON 源码）。 */
    public record VersionSnapshot(int version, String createdBy, java.time.LocalDateTime createdAt,
                                  Map<String, Object> snapshot, String sourceJson) {
    }
}
