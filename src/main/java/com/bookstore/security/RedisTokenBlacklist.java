
package com.bookstore.security;

import java.time.Duration;

import org.springframework.data.redis.core.StringRedisTemplate;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;


@Slf4j
@RequiredArgsConstructor
public class RedisTokenBlacklist implements TokenBlacklist {

    private final StringRedisTemplate redisTemplate;
    private final String BLACKLIST_PREFIX = "jwt:blacklist:";

    @Override
    public void blacklist(String jti, Duration remainingTtl){
        if(remainingTtl.isNegative() || remainingTtl.isZero()){
            return;
        }

        // We store the JTI as the key. The value "revoked" is just a placeholder.
        // Redis handles the cleanup automatically based on remainingTtl.
        redisTemplate.opsForValue().set(BLACKLIST_PREFIX+jti, "revoked", remainingTtl);
        log.info("Token JTI {} blacklisted for {}", jti, remainingTtl);

    }

    @Override
    public boolean isBlackListed(String jti){
        return Boolean.TRUE.equals(redisTemplate.hasKey(BLACKLIST_PREFIX + jti));

    }




}
