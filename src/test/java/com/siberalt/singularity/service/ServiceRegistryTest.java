package com.siberalt.singularity.service;

import com.siberalt.singularity.configuration.ConfigInterface;
import com.siberalt.singularity.configuration.MergeType;
import com.siberalt.singularity.configuration.StringMapConfig;
import com.siberalt.singularity.service.exception.ServiceDependencyException;
import com.siberalt.singularity.service.exception.ServiceNotFoundException;
import com.siberalt.singularity.service.factory.Factory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.*;

interface DummyInterface {
}

public class ServiceRegistryTest {
    private ServiceRegistry serviceRegistry;

    @BeforeEach
    void setUp() {
        serviceRegistry = new ServiceRegistry();
    }

    @Test
    void testSetWithStringServiceId() {
        String serviceId = "testService";
        Object service = new DummyServiceA();
        serviceRegistry.set(serviceId, service);
        Assertions.assertTrue(serviceRegistry.has(serviceId));
        Assertions.assertEquals(service, serviceRegistry.get(serviceId));
    }

    @Test
    void testSetWithClassServiceId() {
        DummyServiceA service = new DummyServiceA();
        serviceRegistry.set(DummyServiceA.class, service);
        Assertions.assertTrue(serviceRegistry.has(DummyServiceA.class));
        Assertions.assertEquals(service, serviceRegistry.get(DummyServiceA.class));
    }

    @Test
    void testSetWithNoServiceId() {
        serviceRegistry.set(new DummyServiceB());
        Assertions.assertTrue(serviceRegistry.has(DummyServiceB.class));
        Assertions.assertNotNull(serviceRegistry.get(DummyServiceB.class));
    }

    @Test
    void testGetWithServiceClass() {
        DummyServiceB service = new DummyServiceB();
        serviceRegistry.set(service);
        Assertions.assertTrue(serviceRegistry.has(DummyServiceB.class));
        Assertions.assertEquals(service, serviceRegistry.get(DummyServiceB.class));
    }

    @Test
    void testGetWithExpectedType() {
        String serviceId = "testService";
        DummyServiceA service = new DummyServiceA();
        serviceRegistry.set(serviceId, service);
        Assertions.assertNotNull(serviceRegistry.get(serviceId, DummyInterface.class));
        Assertions.assertNotNull(serviceRegistry.get(serviceId, DummyServiceA.class));
    }

    @Test
    void testSetFactoryWithClass() {
        Factory factory = mock(Factory.class);
        when(factory.create(any(), any())).thenReturn(new DummyServiceA());
        serviceRegistry.setFactory(DummyServiceA.class, factory);
        Assertions.assertTrue(serviceRegistry.has(DummyServiceA.class));
        // Assert that factory isn't called until the service is required
        verify(factory, never()).create(any(), any());
        Assertions.assertNotNull(serviceRegistry.get(DummyServiceA.class));
    }

    @Test
    void testSetFactoryWithString() {
        String serviceId = "testService";
        Factory factory = mock(Factory.class);
        when(factory.create(any(), any())).thenReturn(new DummyServiceA());
        serviceRegistry.setFactory(serviceId, factory);
        Assertions.assertTrue(serviceRegistry.has(serviceId));
        // Assert that factory isn't called until the service is required
        verify(factory, never()).create(any(), any());
        Assertions.assertNotNull(serviceRegistry.get(serviceId));
    }

    @Test
    void testUnsetWithClass() throws ServiceDependencyException {
        DummyServiceA service = new DummyServiceA();
        serviceRegistry.set(DummyServiceA.class, service);
        Assertions.assertTrue(serviceRegistry.has(DummyServiceA.class));
        serviceRegistry.unset(DummyServiceA.class);
        Assertions.assertFalse(serviceRegistry.has(DummyServiceA.class));
    }

    @Test
    void testUnsetWithString() throws ServiceDependencyException {
        String serviceId = "testService";
        DummyServiceA service = new DummyServiceA();
        serviceRegistry.set(serviceId, service);
        Assertions.assertTrue(serviceRegistry.has(serviceId));
        serviceRegistry.unset(serviceId);
        Assertions.assertFalse(serviceRegistry.has(serviceId));
    }

    @Test
    void testUnsetThrowsException() {
        serviceRegistry.set(new DummyServiceB());
        serviceRegistry.setFactory(
            DummyServiceA.class, (serviceDetails, dependencyManager) -> {
                DummyServiceA dummyServiceA = new DummyServiceA();
                dummyServiceA.setDummyServiceB(dependencyManager.require(DummyServiceB.class));
                return dummyServiceA;
            }
        );
        serviceRegistry.get(DummyServiceA.class);
        Assertions.assertThrows(ServiceDependencyException.class, () -> serviceRegistry.unset(DummyServiceB.class));
    }

    @Test
    void testHasWithString() {
        String serviceId = "testService";
        DummyServiceA service = new DummyServiceA();
        serviceRegistry.set(serviceId, service);
        Assertions.assertTrue(serviceRegistry.has(serviceId));
    }

    @Test
    void testHasWithClass() {
        DummyServiceA service = new DummyServiceA();
        serviceRegistry.set(DummyServiceA.class, service);
        Assertions.assertTrue(serviceRegistry.has(DummyServiceA.class));
    }

    @Test
    void testSetFactoryWithNoConfig(){
        String serviceId = "testService";
        Factory factory = (serviceDetails, dependencyManager) -> {
            ConfigFacade configFacade = ConfigFacade.of(serviceDetails.config());
            DummyServiceA dummyServiceA = new DummyServiceA();
            dummyServiceA.setStringField(configFacade.getAsString("stringField", "default"));
            dummyServiceA.setIntField(configFacade.getAsInt("intField", 0));
            dummyServiceA.setBooleanField(configFacade.getAsBoolean("booleanField", false));
            dummyServiceA.setDummyServiceB(dependencyManager.require(DummyServiceB.class));
            return dummyServiceA;
        };
        DummyServiceB dummyServiceB = new DummyServiceB();
        serviceRegistry.setFactory(serviceId, factory);
        serviceRegistry.set(DummyServiceB.class, dummyServiceB);
        DummyServiceA service = serviceRegistry.get(serviceId, DummyServiceA.class);
        Assertions.assertNotNull(service);
        Assertions.assertEquals("default", service.getStringField());
        Assertions.assertEquals(0, service.getIntField());
        Assertions.assertFalse(service.isBooleanField());
        Assertions.assertSame(dummyServiceB, service.getDummyServiceB());
    }

    @Test
    void testSetFactoryWithConfig(){
        String serviceId = "testService";
        Factory factory = (serviceDetails, dependencyManager) -> {
            ConfigFacade configFacade = ConfigFacade.of(serviceDetails.config());
            DummyServiceA dummyServiceA = new DummyServiceA();
            dummyServiceA.setStringField(configFacade.getAsString("stringField", "default"));
            dummyServiceA.setIntField(configFacade.getAsInt("intField", 0));
            dummyServiceA.setBooleanField(configFacade.getAsBoolean("booleanField", false));
            dummyServiceA.setDummyServiceB(dependencyManager.require(DummyServiceB.class));
            return dummyServiceA;
        };
        DummyServiceB dummyServiceB = new DummyServiceB();
        ConfigInterface config = mock(ConfigInterface.class);
        when(config.get("stringField")).thenReturn("test");
        when(config.get("intField")).thenReturn(42);
        when(config.get("booleanField")).thenReturn(true);

        serviceRegistry.setFactory(serviceId, factory, config);
        serviceRegistry.set(DummyServiceB.class, dummyServiceB);
        DummyServiceA service = serviceRegistry.get(serviceId, DummyServiceA.class);
        Assertions.assertNotNull(service);
        Assertions.assertEquals("test", service.getStringField());
        Assertions.assertEquals(42, service.getIntField());
        Assertions.assertTrue(service.isBooleanField());
        Assertions.assertSame(dummyServiceB, service.getDummyServiceB());
    }

    @Test
    void testReconfigureServiceNotFound() {
        String serviceId = "nonExistentService";
        ConfigInterface newConfig = mock(ConfigInterface.class);

        Assertions.assertThrows(
            ServiceNotFoundException.class,
            () -> serviceRegistry.reconfigure(serviceId, newConfig, MergeType.MERGE)
        );
    }

    @Test
    void testReconfigureServiceWithEmpty() {
        String serviceId = "testService";
        DummyServiceA service = new DummyServiceA();
        ConfigInterface newConfig = mock(ConfigInterface.class);
        Factory factory = mock(Factory.class);
        when(factory.create(any(), any())).thenReturn(service);

        serviceRegistry.setFactory(serviceId, factory);
        serviceRegistry.get(serviceId); // Ensure the service is created

        serviceRegistry.reconfigure(serviceId, newConfig, MergeType.MERGE);

        Definition definition = serviceRegistry.getDefinition(serviceId);
        Assertions.assertNotNull(definition);
    }

    @Test
    void testReconfigureServiceWithMergeUsingFactory() {
        String serviceId = "testService";
        DummyServiceB service = new DummyServiceB();
        serviceRegistry.set(service);
        ConfigInterface initialConfig = new StringMapConfig(
            Map.of(
                "stringField", "test",
                "intField", 42,
                "booleanField", true,
                "listField", List.of("val1", "val2")
            )
        );
        ConfigInterface newConfig = new StringMapConfig(
            Map.of(
                "stringField", "test1",
                "intField", 42,
                "booleanField", false
            )
        );
        Factory factory = (serviceDetails, dependencyManager) -> {
            ConfigFacade configFacade = ConfigFacade.of(serviceDetails.config());
            DummyServiceA dummyServiceA = new DummyServiceA();
            dummyServiceA.setStringField(configFacade.getAsString("stringField", "default"));
            dummyServiceA.setIntField(configFacade.getAsInt("intField", 0));
            dummyServiceA.setBooleanField(configFacade.getAsBoolean("booleanField", false));
            dummyServiceA.setDummyServiceB(dependencyManager.require(DummyServiceB.class));
            dummyServiceA.setListField(configFacade.getAsList("listField", Collections.emptyList()));
            return dummyServiceA;
        };

        serviceRegistry.setFactory(serviceId, factory, initialConfig);
        serviceRegistry.get(serviceId); // Ensure the service is created

        serviceRegistry.reconfigure(serviceId, newConfig, MergeType.MERGE);

        Definition definition = serviceRegistry.getDefinition(serviceId);
        Assertions.assertNotNull(definition);
        DummyServiceA updatedService = (DummyServiceA) serviceRegistry.get(serviceId);
        Assertions.assertEquals("test1", updatedService.getStringField());
        Assertions.assertEquals(42, updatedService.getIntField());
        Assertions.assertFalse(updatedService.isBooleanField());
        Assertions.assertEquals(List.of("val2", "val1"), updatedService.getListField());
    }

    @Test
    void testReconfigureServiceWithReplaceUsingFactory() {
        String serviceId = "testService";
        DummyServiceB service = new DummyServiceB();
        serviceRegistry.set(service);
        ConfigInterface initialConfig = new StringMapConfig(
            Map.of(
                "stringField", "test",
                "intField", 23,
                "booleanField", true,
                "listField", List.of("val3")
            )
        );
        ConfigInterface newConfig = new StringMapConfig(
            Map.of(
                "stringField", "test1",
                "booleanField", false,
                "listField", List.of("val1", "val2")
            )
        );
        Factory factory = (serviceDetails, dependencyManager) -> {
            ConfigFacade configFacade = ConfigFacade.of(serviceDetails.config());
            DummyServiceA dummyServiceA = new DummyServiceA();
            dummyServiceA.setStringField(configFacade.getAsString("stringField", "default"));
            dummyServiceA.setIntField(configFacade.getAsInt("intField", 0));
            dummyServiceA.setBooleanField(configFacade.getAsBoolean("booleanField", false));
            dummyServiceA.setListField(configFacade.getAsList("listField", Collections.emptyList()));
            dummyServiceA.setDummyServiceB(dependencyManager.require(DummyServiceB.class));
            return dummyServiceA;
        };

        serviceRegistry.setFactory(serviceId, factory, initialConfig);
        serviceRegistry.get(serviceId); // Ensure the service is created

        serviceRegistry.reconfigure(serviceId, newConfig, MergeType.REPLACE);

        Definition definition = serviceRegistry.getDefinition(serviceId);
        Assertions.assertNotNull(definition);
        DummyServiceA updatedService = (DummyServiceA) serviceRegistry.get(serviceId);
        Assertions.assertEquals("test1", updatedService.getStringField());
        Assertions.assertEquals(0, updatedService.getIntField());
        Assertions.assertFalse(updatedService.isBooleanField());
        Assertions.assertEquals(List.of("val1", "val2"), updatedService.getListField());
    }

    @Test
    void testReconfigureServiceWithMergeUsingConfigurable() {
        ConfigInterface initialConfig = new StringMapConfig(
            Map.of(
                "stringField", "test",
                "intField", 42,
                "booleanField", true
            )
        );
        ConfigInterface newConfig = new StringMapConfig(
            Map.of(
                "stringField", "test1",
                "intField", 42,
                "booleanField", false
            )
        );

        serviceRegistry.set(DummyServiceB.class, initialConfig);
        DummyServiceB service = serviceRegistry.get(DummyServiceB.class);

        serviceRegistry.reconfigure(DummyServiceB.class, newConfig, MergeType.MERGE);
        Assertions.assertEquals("test1", service.stringField);
        Assertions.assertEquals(42, service.intField);
        Assertions.assertFalse(service.booleanField);
    }
}
