package com.example.tradeengine.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Simple API key authentication filter.  Requires an X-API-Key header
 * matching the configured key for all endpoints except /actuator.
 */
@Component
public class ApiKeyAuthFilter extends OncePerRequestFilter {
    @Value("${app.api-key:secret}")
    private String apiKey;
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        if (path.startsWith("/actuator")) {
            filterChain.doFilter(request, response);
            return;
        }
        String headerKey = request.getHeader("X-API-Key");
        if (headerKey == null || !headerKey.equals(apiKey)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("Unauthorized");
            return;
        }
        filterChain.doFilter(request, response);
    }
}