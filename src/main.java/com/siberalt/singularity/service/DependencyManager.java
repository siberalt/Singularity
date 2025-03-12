package com.siberalt.singularity.service;

import com.siberalt.singularity.service.exception.InvalidServiceTypeException;

import java.util.*;

public class DependencyManager {
    protected ServiceRegistry serviceRegistry;
    // The key is a serviceId, and the value is a set of serviceIds that depend on this service
    protected Map<String, Set<String>> serviceDependencyMap = new HashMap<>();
    protected Stack<String> currentServicesIdsStack = new Stack<>();

    public DependencyManager(ServiceRegistry serviceContainer) {
        this.serviceRegistry = serviceContainer;
    }

    protected void setContextServiceId(String currentServiceId) {
        currentServicesIdsStack.push(currentServiceId);
    }

    protected void resetContextServiceId() {
        currentServicesIdsStack.pop();
    }

    public <T> T require(Class<T> dependencyServiceClass) {
        return require(dependencyServiceClass.getName(), dependencyServiceClass);
    }

    public <T> T require(String dependencyServiceId, Class<T> expected){
        String currentServiceId = currentServicesIdsStack.peek();
        var service = require(dependencyServiceId, currentServiceId);

        if (!expected.isInstance(service)) {
            throw new InvalidServiceTypeException(
                dependencyServiceId,
                expected,
                service.getClass()
            );
        }

        return expected.cast(service);
    }

    public boolean has(String serviceId) {
        return serviceRegistry.has(serviceId);
    }

    public <T> boolean has(Class<T> serviceClass) {
        return serviceRegistry.has(serviceClass);
    }

    public boolean isInUse(String serviceId) {
        return serviceDependencyMap.containsKey(serviceId) && !serviceDependencyMap.get(serviceId).isEmpty();
    }

    protected Object require(String dependencyServiceId, String dependentServiceId) {
        serviceDependencyMap.computeIfAbsent(dependencyServiceId, (x) -> new HashSet<>()).add(dependentServiceId);

        return serviceRegistry.get(dependencyServiceId);
    }

    public void release(String dependencyServiceId) {
        String currentServiceId = currentServicesIdsStack.peek();
        release(dependencyServiceId, currentServiceId);
    }

    public List<String> getDependentServices(String serviceId) {
        return new ArrayList<>(serviceDependencyMap.getOrDefault(serviceId, Collections.emptySet()));
    }

    public List<String> getDependencyServices(String serviceId) {
        List<String> dependencyServices = new ArrayList<>();
        for (var serviceMapEntry : serviceDependencyMap.entrySet()) {
            if (serviceMapEntry.getValue().contains(serviceId)) {
                dependencyServices.add(serviceMapEntry.getKey());
            }
        }
        return dependencyServices;
    }

    protected void releaseForService(String serviceId) {
        for (var serviceMapEntry : serviceDependencyMap.entrySet()) {
            serviceMapEntry.getValue().remove(serviceId);
        }
    }

    protected void release(String dependencyServiceId, String dependentServiceId) {
        if (
            !serviceDependencyMap.containsKey(dependencyServiceId)
                || !serviceDependencyMap.get(dependencyServiceId).contains(dependentServiceId)
        ) {
            return;
        }

        serviceDependencyMap.get(dependencyServiceId).remove(dependentServiceId);
    }
}
