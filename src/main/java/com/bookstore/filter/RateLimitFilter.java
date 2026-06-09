package com.bookstore.filter;

// import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;


import java.io.IOException;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.proxy.ProxyManager;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class RateLimitFilter  extends OncePerRequestFilter{

    // private final Map<String, Bucket> ipBuckets = new ConcurrentHashMap<>();

    private final ProxyManager<String> proxyManager;
    private final BucketConfiguration BucketConfiguration;


    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain filterChain)
    throws ServletException, IOException {

        String clientIp = extractClientIp(request);
        

        //Proxy manager handles the "Get or Create" logic automatically
        Bucket bucket = proxyManager.builder().build(clientIp, () -> BucketConfiguration);

        //tryConsume returns a "Probe" which contains the wait time if rejected
        ConsumptionProbe probe  = bucket.tryConsumeAndReturnRemaining(1);

        if(probe.isConsumed()){
            response.addHeader("X-RateLimit-Remaining", String.valueOf(probe.getRemainingTokens()));
            filterChain.doFilter(request, response);
        }else{
            // Calculate how long the user must wait(in seconds)
            long waitForRefillSeconds = TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill());
            handleRateLimitingError(response, Math.max(1, waitForRefillSeconds));
        }


        
    }


    private void handleRateLimitingError(HttpServletResponse response, Long retryAfter) throws IOException{
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("Retry-After", String.valueOf(retryAfter));

        //Using Text Blocks for clean
        response.getWriter().write("""
            {
                "error": "Too Many Request",
                "message": "You have exceeded your quota. Please try again later",
                "retryAfterSeconds": %d,
                "status": 429
            }
                
                """.formatted(retryAfter));
    }


    private String extractClientIp(HttpServletRequest request){
        String xff = request.getHeader("X-Forwarded-For");
        if(xff != null && !xff.isBlank()){
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }


    
}
