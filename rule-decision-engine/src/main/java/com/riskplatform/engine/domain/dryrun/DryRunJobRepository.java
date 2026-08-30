package com.riskplatform.engine.domain.dryrun;

import java.util.Optional;

/**
 * 试运行任务仓储端口（R5.3）。基础设施层以 MyBatis-Plus 持久化到 {@code dry_run_job} 表。
 */
public interface DryRunJobRepository {

    /**
     * 保存新任务并回填生成的主键。
     *
     * @param job RUNNING 状态的新任务
     * @return 生成的任务 id
     */
    Long save(DryRunJob job);

    /**
     * 更新任务（状态流转与统计结果）。
     *
     * @param job 已有任务（含 id）
     */
    void update(DryRunJob job);

    /**
     * 按 id 查询任务。
     *
     * @param id 任务 id
     * @return 任务（不存在时为空）
     */
    Optional<DryRunJob> findById(long id);
}
