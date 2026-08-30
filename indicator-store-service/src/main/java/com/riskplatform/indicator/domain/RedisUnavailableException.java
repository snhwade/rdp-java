package com.riskplatform.indicator.domain;

/**
 * Redis 不可用异常（R9.4）：读路由据此回退至 ES。
 */
public class RedisUnavailableException extends RuntimeException {
    public RedisUnavailableException(String message) {
        super(message);
    }
}
