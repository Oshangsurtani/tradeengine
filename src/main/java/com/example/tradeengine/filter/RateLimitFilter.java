package com.example.tradeengine.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Simple rate limiting filter.  Limits requests to the /orders endpoint
 * to a fixed number per second (aggregate across all clients).  If the
 * limit is exceeded, returns HTTP 429.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {
    private static final int MAX_REQUESTS_PER_SECOND = 100;
    private final AtomicLong windowStart = new AtomicLong(Instant.now().getEpochSecond());
    private final AtomicInteger count = new AtomicInteger(0);
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        if (path.startsWith("/orders") && request.getMethod().equalsIgnoreCase("POST")) {
            long now = Instant.now().getEpochSecond();
            long start = windowStart.get();
            if (now > start) {
                // reset window
                windowStart.set(now);
                count.set(0);
            }
            if (count.incrementAndGet() > MAX_REQUESTS_PER_SECOND) {
                response.setStatus(429);
                response.getWriter().write("Too Many Requests");
                return;
            }
        }
        filterChain.doFilter(request, response);
    }
}