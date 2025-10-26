package com.finpay.common.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import io.micrometer.tracing.Tracer;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.io.IOException;

/**
 * MDC (Mapped Diagnostic Context) filter for enriching logs with contextual information.
 * Adds trace ID, span ID, and user ID to every log entry for distributed tracing and debugging.
 * Runs once per request to ensure consistent logging context across all services.
 */
@Component
public class LoggingMdcFilter extends OncePerRequestFilter {

    /** Micrometer tracer for distributed tracing support */
    private final Tracer tracer;

    /**
     * Constructor for LoggingMdcFilter.
     *
     * @param tracer Micrometer tracer instance for trace/span context
     */
    @Autowired
    public LoggingMdcFilter(Tracer tracer) {
        this.tracer = tracer;
    }

    /**
     * Filters each HTTP request to populate MDC with trace, span, and user information.
     * This enriches all logs with consistent contextual data for correlation across services.
     *
     * @param request HTTP request
     * @param response HTTP response
     * @param filterChain Filter chain to continue request processing
     * @throws ServletException If servlet error occurs
     * @throws IOException If I/O error occurs
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            // Extract and set trace ID and span ID from Micrometer tracer
            String traceId = tracer.currentSpan() != null ? tracer.currentSpan().context().traceId() : null;
            String spanId = tracer.currentSpan() != null ? tracer.currentSpan().context().spanId() : null;

            if (traceId != null) MDC.put("traceId", traceId);
            if (spanId != null) MDC.put("spanId", spanId);

            // Extract user ID from JWT token in security context
            Object principal = org.springframework.security.core.context.SecurityContextHolder
                    .getContext()
                    .getAuthentication()
                    .getPrincipal();

            if (principal instanceof Jwt jwt) {
                MDC.put("userId", jwt.getClaimAsString("user_id"));
            } else if (principal instanceof JwtAuthenticationToken token) {
                MDC.put("userId", token.getToken().getClaimAsString("user_id"));
            }

            // Continue filter chain with enriched MDC context
            filterChain.doFilter(request, response);
        } finally {
            // Clear MDC after request to prevent memory leaks
            MDC.clear();
        }
    }
}
