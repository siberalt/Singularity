package com.siberalt.singularity.entity.order;

public interface OrderRepository extends ReadOrderRepository, WriteOrderRepository {
    // This interface combines the functionality of both ReadOrderRepository and WriteOrderRepository
    // No additional methods are needed here, as it inherits all necessary methods from the two interfaces.
}
