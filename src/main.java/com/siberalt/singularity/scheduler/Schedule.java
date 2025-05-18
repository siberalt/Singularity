package com.siberalt.singularity.scheduler;

public record Schedule<idType>(idType id, ExecutionIterator iterator) {
    @Override
    public String toString() {
        return "Schedule{" +
            "id=" + id +
            ", iterator=" + iterator +
            '}';
    }
}
