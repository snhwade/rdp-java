package com.riskplatform.engine.domain.dryrun;

/**
 * 试运行任务状态（R5.3，对应 dry_run_job.status）。
 *
 * <ul>
 *   <li>{@link #RUNNING} 运行中：任务已发起、异步执行尚未完成。</li>
 *   <li>{@link #SUCCESS} 成功：全部样本处理完成（含被隔离的异常样本），报告已落库。</li>
 *   <li>{@link #FAILED} 失败：任务级异常（如目标不存在、样本拉取失败）导致整体中断。</li>
 * </ul>
 */
public enum DryRunStatus {
    /** 运行中。 */
    RUNNING,
    /** 成功完成。 */
    SUCCESS,
    /** 失败。 */
    FAILED
}
