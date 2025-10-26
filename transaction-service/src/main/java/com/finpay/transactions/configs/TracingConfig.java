package com.finpay.transactions.configs;

import io.micrometer.tracing.SamplerFunction;
import io.micrometer.tracing.Tracer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration class for distributed tracing.
 * Configures Micrometer tracing with sampling strategy for observability.
 */
@Configuration
public class TracingConfig {

    /**
     * Creates a sampler function that always samples traces.
     * This means all requests will be traced for monitoring and debugging purposes.
     * In production, you might want to use a probabilistic sampler to reduce overhead.
     *
     * @return SamplerFunction configured to sample all traces
     */
    @Bean
    public SamplerFunction<Tracer> defaultSampler() {
        return SamplerFunction.alwaysSample();
    }
}
