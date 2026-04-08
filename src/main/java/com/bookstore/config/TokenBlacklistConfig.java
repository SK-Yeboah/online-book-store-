package com.bookstore.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.bookstore.security.CaffeineTokenBlacklist;
import com.bookstore.security.RedisTokenBlacklist;
import com.bookstore.security.TokenBlacklist;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TokenBlacklistConfig  {


    //Production profile: Uses Redis for distributee environment 
    //Activated by: String.profile.active = prod

    @Bean
    @Profile("prod")
    public TokenBlacklist redTokenBlacklist(StringRedisTemplate redisTemplate){
        return new RedisTokenBlacklist(redisTemplate);
    }


    // Dev/Default Profile: Uses Caffeine for local development 
    // Activated when no profile is set, or spring.profiles.active=dev
    @Bean
    @Profile("!prod")
    public TokenBlacklist caffeineTokenBlacklist(){
        return new CaffeineTokenBlacklist();
    }
    
}
