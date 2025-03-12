package com.siberalt.singularity.configuration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class MergedConfigTest {
    private ConfigInterface configA;
    private ConfigInterface configB;

    @BeforeEach
    void setUp() {
        configA = mock(ConfigInterface.class);
        configB = mock(ConfigInterface.class);
    }

    @Test
    void testGetMerge() {
        when(configA.get("key")).thenReturn("valueA");
        when(configA.get("key1")).thenReturn("valueA1");
        when(configB.get("key")).thenReturn("valueB");
        when(configB.has("key")).thenReturn(true);

        MergedConfig mergedConfig = new MergedConfig(configA, configB, MergeType.MERGE);
        assertEquals("valueB", mergedConfig.get("key"));
        assertEquals("valueA1", mergedConfig.get("key1"));
    }

    @Test
    void testGetReplace() {
        when(configA.get("key")).thenReturn("valueA");
        when(configA.get("key1")).thenReturn("valueA1");
        when(configB.get("key")).thenReturn("valueB");

        MergedConfig mergedConfig = new MergedConfig(configA, configB, MergeType.REPLACE);
        assertEquals("valueB", mergedConfig.get("key"));
        assertFalse(mergedConfig.has("key1"));
    }

    @Test
    void testGetAsListMerge() {
        when(configA.getAsList("key")).thenReturn(List.of("valueA"));
        when(configB.getAsList("key")).thenReturn(List.of("valueB"));

        MergedConfig mergedConfig = new MergedConfig(configA, configB, MergeType.MERGE);
        List<Object> result = mergedConfig.getAsList("key");
        assertTrue(result.contains("valueA"));
        assertTrue(result.contains("valueB"));
    }

    @Test
    void testGetAsListReplace() {
        when(configB.getAsList("key")).thenReturn(List.of("valueB"));

        MergedConfig mergedConfig = new MergedConfig(configA, configB, MergeType.REPLACE);
        List<Object> result = mergedConfig.getAsList("key");
        assertEquals(List.of("valueB"), result);
    }

    @Test
    void testGetAsMapMerge() {
        when(configA.getAsMap("key")).thenReturn(Map.of("keyA", "valueA"));
        when(configB.getAsMap("key")).thenReturn(Map.of("keyB", "valueB"));

        MergedConfig mergedConfig = new MergedConfig(configA, configB, MergeType.MERGE);
        Map<String, Object> result = mergedConfig.getAsMap("key");
        assertEquals(2, result.size());
        assertEquals("valueA", result.get("keyA"));
        assertEquals("valueB", result.get("keyB"));
    }

    @Test
    void testGetAsMapReplace() {
        when(configB.getAsMap("key")).thenReturn(Map.of("keyB", "valueB"));

        MergedConfig mergedConfig = new MergedConfig(configA, configB, MergeType.REPLACE);
        Map<String, Object> result = mergedConfig.getAsMap("key");
        assertEquals(1, result.size());
        assertEquals("valueB", result.get("keyB"));
    }

    @Test
    void testHasMerge() {
        when(configA.has("key")).thenReturn(true);
        when(configB.has("key")).thenReturn(false);

        MergedConfig mergedConfig = new MergedConfig(configA, configB, MergeType.MERGE);
        assertTrue(mergedConfig.has("key"));
    }

    @Test
    void testHasReplace() {
        when(configB.has("key")).thenReturn(true);

        MergedConfig mergedConfig = new MergedConfig(configA, configB, MergeType.REPLACE);
        assertTrue(mergedConfig.has("key"));
    }
}