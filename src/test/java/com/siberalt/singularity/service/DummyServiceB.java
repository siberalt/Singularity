package com.siberalt.singularity.service;

public class DummyServiceB implements DummyInterface, Configurable {
    public String stringField;
    public int intField;
    public boolean booleanField;

    public DummyServiceB() {
    }

    @Override
    public void configure(ServiceDetails serviceDetails, DependencyManager dependencyManager) {
        ConfigFacade configFacade = ConfigFacade.of(serviceDetails.config());
        this.stringField = configFacade.getAsString("stringField", "default");
        this.intField = configFacade.getAsInt("intField", 0);
        this.booleanField = configFacade.getAsBoolean("booleanField", false);
    }
}
