package com.riskplatform.engine.domain.list;

import java.util.Map;

/**
 * 决策流 LIST_CHECK 节点的名单命中端口（enhancement-plan T4）。
 */
public interface ListCheckPort {

    /**
     * 对上下文中主体字段做精确名单检查。
     *
     * @return 命中摘要；调用失败时实现方可返回 empty（由节点回退上下文注入）
     */
    ListHit check(Map<String, Object> context);

    record ListHit(boolean blackHit, boolean watchHit, boolean whiteHit, boolean fromService) {
        public static ListHit empty() {
            return new ListHit(false, false, false, false);
        }

        public static ListHit fromContext(boolean black, boolean watch) {
            return new ListHit(black, watch, false, false);
        }
    }
}
