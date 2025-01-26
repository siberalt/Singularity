package com.siberalt.singularity.strategy.impl;

import com.siberalt.singularity.strategy.context.AbstractContext;
import com.siberalt.singularity.strategy.StrategyInterface;

public class TinkoffIMOEXStrategy implements StrategyInterface {
    protected String id;

    @Override
    public String getId() {
        return id;
    }

    @Override
    public void setId(String id) {
        this.id = id;
    }

    @Override
    public void start(AbstractContext<?> context) {

    }

    @Override
    public void run(AbstractContext<?> context) {

    }

    @Override
    public void stop(AbstractContext<?> context) {

    }
}
