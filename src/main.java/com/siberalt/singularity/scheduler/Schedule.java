package com.siberalt.singularity.scheduler;

import java.util.function.Consumer;

public record Schedule<idType>(idType id, ExecutionIterator iterator, Consumer<Schedule<idType>> onFinish) {
    public Schedule(idType id, ExecutionIterator iterator) {
        this(id, iterator, null);
    }

    @Override
    public String toString() {
        return "Schedule{" +
            "id=" + id +
            ", iterator=" + iterator +
            '}';
    }
}
