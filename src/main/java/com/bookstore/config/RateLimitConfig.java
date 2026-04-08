package com.bookstore.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.github.bucket4j.distributed.proxy.ProxyManager;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.caffeine.CaffeineProxyManager;

import com.github.benmanes.caffeine.cache.Caffeine;

@Configuration
public class RateLimitConfig {

    @Value("${security.rate-limiting.capacity:60}")
    private int capacity;

    @Value("${security.rate-limiting.refill-per-minute:60}")
    private int refill;

    @Bean
    public BucketConfiguration bucketConfiguration() {
        return BucketConfiguration.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(capacity)
                        .refillGreedy(refill, Duration.ofMinutes(1))
                        .build())
                .build();
    }

    @Bean
    public ProxyManager<String> proxyManager() {
        Caffeine<Object, Object> caffeine = Caffeine.newBuilder()
                .maximumSize(10000);
        return new CaffeineProxyManager<String>(caffeine, Duration.ofMinutes(10));
    }
    
}
