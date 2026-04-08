package com.bookstore.security;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Cache;
import java.util.concurrent.TimeUnit;
import java.time.Duration;

public class CaffeineTokenBlacklist implements TokenBlacklist{

    // Cache that automatically removes entries after 24 hour(fallback)
    private final Cache<String, Boolean> cache = Caffeine.newBuilder()
        .expireAfterWrite(24, TimeUnit.HOURS)
        .maximumSize(10_000)
        .build();


    @Override
    public void blacklist(String jti, Duration remainingTtl){
        //In local dev, we just put it in the cache
        cache.put(jti, true);
    }

    @Override
    public boolean isBlackListed(String jti){
        return cache.getIfPresent(jti) != null;
    }
    
}
