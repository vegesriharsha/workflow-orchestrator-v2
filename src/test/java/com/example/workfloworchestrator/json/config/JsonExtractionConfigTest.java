package com.example.workfloworchestrator.json.config;

import com.example.workfloworchestrator.json.transformation.AttributeTransformer;
import com.example.workfloworchestrator.json.transformation.DateFormatTransformer;
import com.example.workfloworchestrator.json.transformation.ValueMappingTransformer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class JsonExtractionConfigTest {

    @Autowired
    private Map<String, AttributeTransformer> attributeTransformers;

    @Autowired
    private CacheManager jsonExtractionCacheManager;

    @Autowired
    private JsonExtractionConfig config;

    @Test
    void shouldConfigureAttributeTransformers() {
        // Then
        assertThat(attributeTransformers).isNotEmpty();
        assertThat(attributeTransformers).containsKeys("DATE_FORMAT", "VALUE_MAP");
        assertThat(attributeTransformers.get("DATE_FORMAT")).isInstanceOf(DateFormatTransformer.class);
        assertThat(attributeTransformers.get("VALUE_MAP")).isInstanceOf(ValueMappingTransformer.class);
    }

    @Test
    void shouldConfigureCacheManager() {
        // Then
        assertThat(jsonExtractionCacheManager).isNotNull();
        assertThat(jsonExtractionCacheManager.getCacheNames()).contains(
                "extractedRequests", "attributeMappings", "transformationResults"
        );
    }

    @Test
    void shouldCreateTransformerMapFromList() {
        // Given
        DateFormatTransformer dateTransformer = new DateFormatTransformer(null);
        ValueMappingTransformer valueTransformer = new ValueMappingTransformer(null);
        List<AttributeTransformer> transformers = List.of(dateTransformer, valueTransformer);

        // When
        Map<String, AttributeTransformer> result = config.attributeTransformers(transformers);

        // Then
        assertThat(result).hasSize(2);
        assertThat(result.get("DATE_FORMAT")).isEqualTo(dateTransformer);
        assertThat(result.get("VALUE_MAP")).isEqualTo(valueTransformer);
    }

    @Test
    void shouldHandleEmptyTransformerList() {
        // Given
        List<AttributeTransformer> emptyList = List.of();

        // When
        Map<String, AttributeTransformer> result = config.attributeTransformers(emptyList);

        // Then
        assertThat(result).isEmpty();
    }
}
