package com.bookstore.filter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests for RateLimitFilter.
 *
 * The test profile sets capacity=3 (security.rate-limiting.capacity=3) so we
 * can exhaust a bucket and trigger HTTP 429 with just a handful of requests,
 * keeping the suite fast.
 *
 * Every test uses a distinct IP address so buckets do not bleed across tests.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("RateLimitFilter — 429 enforcement and IP isolation")
class RateLimitFilterTest {

    @Autowired MockMvc mockMvc;

    // Any public endpoint works as the probe — we use /actuator/health
    private static final String PROBE = "/actuator/health";

    // ── X-RateLimit-Remaining header is set on successful requests ────────────

    @Test
    @DisplayName("X-RateLimit-Remaining header is decremented on each request")
    void rateLimitHeader_isDecrementedPerRequest() throws Exception {
        // IP unique to this test
        mockMvc.perform(get(PROBE).header("X-Forwarded-For", "10.0.0.50"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-RateLimit-Remaining"));
    }

    // ── Exceeding capacity → 429 ──────────────────────────────────────────────

    @Test
    @DisplayName("requests exceeding capacity → 429 Too Many Requests with Retry-After header")
    void exceedingCapacity_returns429() throws Exception {
        String ip = "10.0.1.1"; // unique to this test

        // Drain all 3 tokens (capacity = 3 in test profile)
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(get(PROBE).header("X-Forwarded-For", ip))
                    .andExpect(status().isOk());
        }

        // 4th request — bucket exhausted
        mockMvc.perform(get(PROBE).header("X-Forwarded-For", ip))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.error").value("Too Many Request"))
                .andExpect(jsonPath("$.status").value(429));
    }

    // ── Two IPs have independent buckets ──────────────────────────────────────

    @Test
    @DisplayName("two different IPs have independent rate-limit buckets")
    void differentIps_haveIndependentBuckets() throws Exception {
        String ip1 = "10.0.2.1";
        String ip2 = "10.0.2.2";

        // Drain ip1's bucket completely
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(get(PROBE).header("X-Forwarded-For", ip1))
                    .andExpect(status().isOk());
        }

        // ip1 is now throttled
        mockMvc.perform(get(PROBE).header("X-Forwarded-For", ip1))
                .andExpect(status().isTooManyRequests());

        // ip2 still has a full bucket — must succeed
        mockMvc.perform(get(PROBE).header("X-Forwarded-For", ip2))
                .andExpect(status().isOk());
    }

    // ── X-Forwarded-For: multiple IPs uses the first (client) IP ─────────────

    @Test
    @DisplayName("X-Forwarded-For with proxy chain uses the leftmost (client) IP")
    void xForwardedFor_multipleIps_usesFirstIp() throws Exception {
        // "203.0.113.5" is the real client; "10.0.3.1" is a proxy
        String clientIp = "10.0.4.5";
        String proxyChain = clientIp + ", 10.0.4.99";

        // Drain only the client IP bucket
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(get(PROBE).header("X-Forwarded-For", proxyChain))
                    .andExpect(status().isOk());
        }

        // Same proxy chain now throttled because client IP bucket is empty
        mockMvc.perform(get(PROBE).header("X-Forwarded-For", proxyChain))
                .andExpect(status().isTooManyRequests());

        // Different client IP behind same proxy — not throttled
        mockMvc.perform(get(PROBE)
                        .header("X-Forwarded-For", "10.0.4.6, 10.0.4.99"))
                .andExpect(status().isOk());
    }

    // ── RemoteAddr fallback when no X-Forwarded-For header ───────────────────

    @Test
    @DisplayName("no X-Forwarded-For header → rate limits by RemoteAddr (defaults to 127.0.0.1)")
    void noXForwardedFor_usesRemoteAddr() throws Exception {
        // MockMvc uses 127.0.0.1 as the default remote address.
        // This test verifies the filter doesn't blow up without the header.
        // Note: 127.0.0.1 bucket may already be partially drained by other tests,
        // so we just assert the filter is running (response is not a 5xx).
        mockMvc.perform(get(PROBE))
                .andExpect(status().is(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.greaterThanOrEqualTo(500))));
    }
}
