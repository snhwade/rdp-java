package com.riskplatform.engine.infrastructure.standalone;

import com.riskplatform.engine.domain.list.ListCheckPort;

import java.util.Map;

/**
 * standalone 模式：不调筛查服务，由节点回退上下文 blackHit/watchHit。
 */
public class ContextOnlyListCheckPort implements ListCheckPort {

    @Override
    public ListHit check(Map<String, Object> context) {
        return ListHit.empty();
    }
}
