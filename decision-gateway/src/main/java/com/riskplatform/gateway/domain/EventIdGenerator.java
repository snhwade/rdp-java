package com.riskplatform.gateway.domain;

/**
 * 事件标识生成端口（R2.7）：生成全局唯一事件标识。
 */
@FunctionalInterface
public interface EventIdGenerator {
    String generate();
}
