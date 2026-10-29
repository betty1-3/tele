package com.hospital.dashboard.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import org.apache.http.HttpHost;
import org.apache.http.Header;
import org.apache.http.message.BasicHeader;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "app.elasticsearch.enabled", havingValue = "true")
public class ElasticsearchConfig {
    @Bean(destroyMethod = "close")
    RestClientTransport elasticsearchTransport(@Value("${app.elasticsearch.url}") String url,
            @Value("${app.elasticsearch.api-key:}") String apiKey) {
        RestClientBuilder builder = RestClient.builder(HttpHost.create(url));
        if (!apiKey.isBlank()) builder.setDefaultHeaders(new Header[]{new BasicHeader("Authorization", "ApiKey " + apiKey)});
        return new RestClientTransport(builder.build(), new JacksonJsonpMapper());
    }

    @Bean
    ElasticsearchClient elasticsearchClient(RestClientTransport transport) {
        return new ElasticsearchClient(transport);
    }
}
