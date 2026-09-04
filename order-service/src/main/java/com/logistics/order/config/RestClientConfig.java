package com.logistics.order.config;

import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * The outbound HTTP client.
 *
 * <p>Every call this service makes carries an explicit connect and read timeout. That is not a
 * detail: the JDK default is to wait forever, so one hung downstream would pin a request thread
 * indefinitely, and a few dozen of those exhaust the pool and take this service down with it. A
 * bounded wait turns someone else's outage into a fast 503 here.
 */
@Configuration
public class RestClientConfig {

    @Bean
    public RestClient downstreamRestClient(DownstreamProperties properties) {
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.DEFAULTS
                .withConnectTimeout(properties.connectTimeout())
                .withReadTimeout(properties.readTimeout());

        return RestClient.builder()
                .baseUrl(properties.baseUri())
                .requestFactory(ClientHttpRequestFactories.get(settings))
                .build();
    }
}
