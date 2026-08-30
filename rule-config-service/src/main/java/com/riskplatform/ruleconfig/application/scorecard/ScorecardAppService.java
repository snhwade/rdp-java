package com.riskplatform.ruleconfig.application.scorecard;

import com.riskplatform.common.error.BizException;
import com.riskplatform.ruleconfig.domain.config.ConfigChangePublisher;
import com.riskplatform.ruleconfig.domain.scorecard.Scorecard;
import com.riskplatform.ruleconfig.domain.scorecard.ScorecardRepository;

import java.util.List;

/** 评分卡应用服务（S3）：CRUD + 配置变更广播。 */
public class ScorecardAppService {

    private final ScorecardRepository repository;
    private final ConfigChangePublisher configChangePublisher;

    public ScorecardAppService(ScorecardRepository repository, ConfigChangePublisher configChangePublisher) {
        this.repository = repository;
        this.configChangePublisher = configChangePublisher;
    }

    public Scorecard create(String name, String eventTypeCode,
                            List<Scorecard.Variable> variables, List<Scorecard.Level> levels) {
        Scorecard s = Scorecard.create(name, eventTypeCode, variables, levels);
        Scorecard saved = repository.save(s);
        configChangePublisher.publishChange("SCORECARD", String.valueOf(saved.getId()));
        return saved;
    }

    public Scorecard update(Long id, String name, List<Scorecard.Variable> variables,
                            List<Scorecard.Level> levels, String status) {
        Scorecard s = repository.findById(id)
                .orElseThrow(() -> BizException.notFound("评分卡不存在: id=" + id));
        s.update(name, variables, levels, status);
        Scorecard saved = repository.update(s);
        configChangePublisher.publishChange("SCORECARD", String.valueOf(id));
        return saved;
    }

    public void delete(Long id) {
        if (repository.findById(id).isEmpty()) {
            throw BizException.notFound("评分卡不存在: id=" + id);
        }
        repository.deleteById(id);
        configChangePublisher.publishChange("SCORECARD", String.valueOf(id));
    }

    public List<Scorecard> list(String eventTypeCode) {
        return (eventTypeCode == null || eventTypeCode.isBlank())
                ? repository.findAll() : repository.findByEventTypeCode(eventTypeCode);
    }

    public Scorecard get(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> BizException.notFound("评分卡不存在: id=" + id));
    }
}
