package com.riskplatform.indicator.infrastructure.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.riskplatform.indicator.domain.EsStore;
import com.riskplatform.indicator.infrastructure.es.ElasticsearchEsStore;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Elasticsearch 客户端与 {@link EsStore} 装配（R9.1/R9.4/R9.5）。
 *
 * <p>使用官方 Elasticsearch Java Client（{@link ElasticsearchClient}）经
 * {@link RestClientTransport} 连接，供 {@link ElasticsearchEsStore} 进行
 * 指标切片的读写与窗口聚合。连接参数来自配置项 {@code indicator.es.*}。
 */
@Configuration
public class ElasticsearchConfig {

    @Bean(destroyMethod = "close")
    public RestClient esRestClient(
            @Value("${indicator.es.host:localhost}") String host,
            @Value("${indicator.es.port:9200}") int port,
            @Value("${indicator.es.scheme:http}") String scheme) {
        return RestClient.builder(new HttpHost(host, port, scheme)).build();
    }

    @Bean(destroyMethod = "close")
    public ElasticsearchTransport esTransport(RestClient restClient) {
        return new RestClientTransport(restClient, new JacksonJsonpMapper());
    }

    @Bean
    public ElasticsearchClient elasticsearchClient(ElasticsearchTransport transport) {
        return new ElasticsearchClient(transport);
    }

    @Bean
    public EsStore esStore(ElasticsearchClient client) {
        return new ElasticsearchEsStore(client);
    }
}
