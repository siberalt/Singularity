package com.siberalt.singularity.service;

import com.siberalt.singularity.service.factory.FactoryInterface;

public record Definition(
    ServiceDetails serviceDetails,
    FactoryInterface factory,
    Configurator configurator,
    ConfigSnapshot configSnapshot
) {
    public Definition(ServiceDetails serviceDetails) {
        this(serviceDetails, null, null, null);
    }

    public Definition updateServiceDetails(ServiceDetails serviceDetails) {
        return new Definition(serviceDetails, this.factory, this.configurator, this.configSnapshot);
    }

    public Definition updateFactory(FactoryInterface factory) {
        return new Definition(this.serviceDetails, factory, this.configurator, this.configSnapshot);
    }

    public Definition updateConfigurator(Configurator configurator) {
        return new Definition(this.serviceDetails, this.factory, configurator, this.configSnapshot);
    }

    public Definition updateConfigSnapshot(ConfigSnapshot configSnapshot) {
        return new Definition(this.serviceDetails, this.factory, this.configurator, configSnapshot);
    }

    public static Definition of(ServiceDetails serviceDetails) {
        return new Definition(serviceDetails);
    }
}
