package com.riskplatform.bff.domain;

import java.util.Map;

/**
 * 下游服务调用端口（R14.1/R14.2/R17.1）。
 *
 * <p>BFF 通过该端口向后端服务转发页面级聚合请求，并透传 JWT（Authorization 头）。
 * 实现需保证：
 * <ul>
 *   <li>JWT 透传：将调用方的 Authorization 头原样转发给下游（R17.1）；</li>
 *   <li>错误透传：下游返回的结构化错误体 {@code { code, message, fields }} 原样向上抛出，
 *       保证字段级校验错误能映射到前端表单项（R14.2）。</li>
 * </ul>
 *
 * <p>抽离为端口以便聚合逻辑可用替身做契约测试，无需真实下游服务。
 */
public interface DownstreamClient {

    /**
     * 转发 GET 请求。
     *
     * @param baseUrl       下游服务基地址
     * @param path          请求路径（含已编码的查询串）
     * @param authorization 调用方 Authorization 头（可空，透传给下游）
     * @return 下游响应体（反序列化为通用结构）
     */
    Object get(String baseUrl, String path, String authorization);

    /**
     * 转发 POST 请求。
     *
     * @param baseUrl       下游服务基地址
     * @param path          请求路径
     * @param body          请求体
     * @param authorization 调用方 Authorization 头（可空，透传给下游）
     * @return 下游响应体
     */
    Object post(String baseUrl, String path, Object body, String authorization);

    /**
     * 转发 PUT 请求。
     */
    Object put(String baseUrl, String path, Object body, String authorization);

    /**
     * 转发 DELETE 请求。
     */
    Object delete(String baseUrl, String path, String authorization);

    /**
     * 下游错误（承载结构化错误体，供 BFF 适配器透传给前端，R14.2）。
     */
    class DownstreamException extends RuntimeException {
        private final int status;
        private final String code;
        private final transient Map<String, String> fields;

        public DownstreamException(int status, String code, String message, Map<String, String> fields) {
            super(message);
            this.status = status;
            this.code = code;
            this.fields = fields;
        }

        public int status() {
            return status;
        }

        public String code() {
            return code;
        }

        public Map<String, String> fields() {
            return fields;
        }
    }
}
