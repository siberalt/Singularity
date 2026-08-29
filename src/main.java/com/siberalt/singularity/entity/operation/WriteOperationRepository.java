package com.siberalt.singularity.entity.operation;

public interface WriteOperationRepository {
    void save(Operation operation);

    default void saveAll(Iterable<Operation> operations) {
        for (Operation operation : operations) {
            if (operation != null) {
                save(operation);
            }
        }
    }
}
