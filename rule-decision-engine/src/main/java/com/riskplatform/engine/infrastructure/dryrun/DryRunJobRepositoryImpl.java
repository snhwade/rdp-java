package com.riskplatform.engine.infrastructure.dryrun;

import com.riskplatform.engine.domain.dryrun.DryRunJob;
import com.riskplatform.engine.domain.dryrun.DryRunJobRepository;
import com.riskplatform.engine.domain.dryrun.DryRunSampleSource;
import com.riskplatform.engine.domain.dryrun.DryRunStatus;
import com.riskplatform.engine.domain.dryrun.DryRunTargetType;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 试运行任务仓储 MyBatis-Plus 实现（V17，R5.3）。
 *
 * <p>领域聚合 {@link DryRunJob} ↔ 持久化对象 {@link DryRunJobPO} 互转，落 {@code dry_run_job} 表。
 */
@Repository
public class DryRunJobRepositoryImpl implements DryRunJobRepository {

    private final DryRunJobMapper mapper;

    public DryRunJobRepositoryImpl(DryRunJobMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Long save(DryRunJob job) {
        DryRunJobPO po = toPO(job);
        mapper.insert(po);
        job.assignId(po.getId());
        return po.getId();
    }

    @Override
    public void update(DryRunJob job) {
        mapper.updateById(toPO(job));
    }

    @Override
    public Optional<DryRunJob> findById(long id) {
        DryRunJobPO po = mapper.selectById(id);
        return Optional.ofNullable(po).map(this::toDomain);
    }

    private DryRunJobPO toPO(DryRunJob job) {
        DryRunJobPO po = new DryRunJobPO();
        po.setId(job.getId());
        po.setTargetType(job.getTargetType().name());
        po.setTargetId(job.getTargetId());
        po.setSampleSource(job.getSampleSource().name());
        po.setDataFrom(job.getDataFrom());
        po.setDataTo(job.getDataTo());
        po.setSampleLimit(job.getSampleLimit());
        po.setStatus(job.getStatus().name());
        po.setTotalCount(job.getTotalCount());
        po.setHitCount(job.getHitCount());
        po.setHitRate(job.getHitRate());
        po.setErrorCount(job.getErrorCount());
        po.setReportJson(job.getReportJson());
        po.setCreatedBy(job.getCreatedBy());
        po.setStartedAt(job.getStartedAt());
        po.setFinishedAt(job.getFinishedAt());
        return po;
    }

    private DryRunJob toDomain(DryRunJobPO po) {
        return DryRunJob.rehydrate(
                po.getId(),
                DryRunTargetType.valueOf(po.getTargetType()),
                po.getTargetId() == null ? 0L : po.getTargetId(),
                DryRunSampleSource.valueOf(po.getSampleSource()),
                po.getDataFrom(),
                po.getDataTo(),
                po.getSampleLimit() == null ? 0 : po.getSampleLimit(),
                DryRunStatus.valueOf(po.getStatus()),
                po.getTotalCount() == null ? 0 : po.getTotalCount(),
                po.getHitCount() == null ? 0 : po.getHitCount(),
                po.getHitRate(),
                po.getErrorCount() == null ? 0 : po.getErrorCount(),
                po.getReportJson(),
                po.getCreatedBy(),
                po.getStartedAt(),
                po.getFinishedAt());
    }
}
