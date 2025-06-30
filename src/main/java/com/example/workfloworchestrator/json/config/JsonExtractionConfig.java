package com.example.workfloworchestrator.json.config;

import com.example.workfloworchestrator.json.transformation.AttributeTransformer;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Configuration for JSON extraction and caching
 */
@Configuration
@EnableCaching
public class JsonExtractionConfig {

    /**
     * Create a map of transformer types to transformer instances
     *
     * @param transformers list of transformer instances
     * @return map of transformer types to transformers
     */
    @Bean
    public Map<String, AttributeTransformer> attributeTransformers(List<AttributeTransformer> transformers) {
        Map<String, AttributeTransformer> transformerMap = new HashMap<>();

        // Register each transformer by its type
        for (AttributeTransformer transformer : transformers) {
            transformerMap.put(transformer.getTransformerType(), transformer);
        }

        return transformerMap;
    }

    /**
     * Cache manager for JSON extraction caching
     * Configured with Caffeine for high performance in-memory caching
     */
    @Bean
    public CacheManager jsonExtractionCacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        
        // Configure cache settings
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(1000)                          // Maximum number of entries
                .expireAfterWrite(Duration.ofMinutes(30))   // TTL for cache entries
                .recordStats()                              // Enable metrics collection
        );

        // Register cache names
        cacheManager.setCacheNames(List.of(
                "extractedRequests",     // Cache for built REST API requests
                "attributeMappings",     // Cache for task attribute mappings
                "transformationResults"  // Cache for transformation results
        ));

        return cacheManager;
    }
}
