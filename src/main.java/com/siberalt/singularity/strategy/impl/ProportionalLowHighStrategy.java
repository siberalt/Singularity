package com.siberalt.singularity.strategy.impl;

import com.siberalt.singularity.strategy.StrategyCommand;
import com.siberalt.singularity.strategy.context.AbstractContext;
import com.siberalt.singularity.strategy.StrategyInterface;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

public class ProportionalLowHighStrategy implements StrategyInterface, Externalizable {

    @Override
    public void execute(StrategyCommand command) {

    }

    @Override
    public void applyContext(AbstractContext<?> context) {

    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {

    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
    }
}
