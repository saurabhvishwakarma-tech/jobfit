package com.jobfit.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Fixed-window rate limiter for the unauthenticated auth endpoints
 * (register/login/refresh) - the classic brute-force/credential-stuffing
 * target. Hand-rolled and in-memory rather than a Bucket4j/Redis-backed
 * one: it's honest about the tradeoff (see class-level limitation note
 * below) rather than pulling in infrastructure this single-instance
 * portfolio deployment doesn't have.
 *
 * LIMITATION: per-instance in-memory state. Correct for the single-ECS-task
 * deployment this project ships (see docs/DEPLOYMENT.md) - would need a
 * shared store (Redis, or an edge solution like an API Gateway usage plan/
 * WAF rate rule) the moment this runs behind more than one instance.
 */
@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final int maxRequestsPerWindow;
    private final ConcurrentHashMap<String, RequestWindow> windowsByKey = new ConcurrentHashMap<>();

    public RateLimitingFilter(
            @Value("${jobfit.security.rate-limit.auth-requests-per-minute:20}") int maxRequestsPerWindow) {
        this.maxRequestsPerWindow = maxRequestsPerWindow;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                     @NonNull FilterChain filterChain) throws ServletException, IOException {
        if (!isRateLimitedPath(request) || maxRequestsPerWindow <= 0) {
            filterChain.doFilter(request, response);
            return;
        }

        String key = clientKey(request);
        RequestWindow window = windowsByKey.computeIfAbsent(key, k -> new RequestWindow());
        if (window.tryConsume(maxRequestsPerWindow, WINDOW)) {
            filterChain.doFilter(request, response);
        } else {
            response.setStatus(429);
            response.setHeader("Retry-After", String.valueOf(WINDOW.toSeconds()));
            response.setContentType("application/json");
            response.getWriter().write(
                    "{\"status\":429,\"error\":\"Too Many Requests\","
                            + "\"message\":\"Too many requests - please wait a moment and try again.\"}");
        }
    }

    private boolean isRateLimitedPath(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/api/auth/");
    }

    /**
     * Keyed by client IP + path, so a burst against /login doesn't also
     * lock the same caller out of /register. Honors X-Forwarded-For since
     * this sits behind an ALB in the deployed architecture (see
     * docs/DEPLOYMENT.md) - trusted there because the ALB, not the client,
     * sets that header before it reaches the container.
     */
    private String clientKey(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        String ip = (forwardedFor != null && !forwardedFor.isBlank())
                ? forwardedFor.split(",")[0].trim()
                : request.getRemoteAddr();
        return ip + ":" + request.getRequestURI();
    }

    private static final class RequestWindow {
        private final AtomicInteger count = new AtomicInteger(0);
        private final AtomicReference<Instant> windowStart = new AtomicReference<>(Instant.now());

        synchronized boolean tryConsume(int max, Duration window) {
            Instant now = Instant.now();
            if (Duration.between(windowStart.get(), now).compareTo(window) >= 0) {
                windowStart.set(now);
                count.set(0);
            }
            return count.incrementAndGet() <= max;
        }
    }
}
