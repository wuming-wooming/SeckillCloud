package org.sangoria.seckillorder.config;

import org.sangoria.seckillapi.client.ProductStockClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

/**
 * T4.4 ProductStockClient 装配。
 * <p>为什么手动构建 HttpServiceProxyFactory 而不是用 @ImportHttpServices：
 * <ul>
 *   <li>契约接口在 seckill-api，本模块只消费；手动构建更明确依赖边界</li>
 *   <li>@LoadBalanced RestClient.Builder 是 Spring Cloud LoadBalancer 支持的入口，
 *       baseUrl 用 lb://{service-name} 才能解析到 Nacos 实例</li>
 *   <li>超时通过 SimpleClientHttpRequestFactory 精确控制</li>
 * </ul>
 */
@Configuration
public class HttpClientConfig {

    @Bean
    @LoadBalanced
    public RestClient.Builder loadBalancedRestClientBuilder(
            @Value("${seckill.order.product-call-timeout-ms:3000}") int timeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeoutMs);
        factory.setReadTimeout(timeoutMs);
        return RestClient.builder().requestFactory(factory);
    }

    @Bean
    public ProductStockClient productStockClient(
            @LoadBalanced RestClient.Builder builder,
            @Value("${seckill.order.product-service-name:seckill-product}") String serviceName) {
        RestClient client = builder
                .baseUrl("http://" + serviceName)
                .build();
        HttpServiceProxyFactory factory = HttpServiceProxyFactory
                .builderFor(RestClientAdapter.create(client))
                .build();
        return factory.createClient(ProductStockClient.class);
    }
}