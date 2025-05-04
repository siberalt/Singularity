package com.siberalt.singularity.entity.order;

public interface WriteOrderRepository {
    void save(Order order);

    void delete(Order order);

    void deleteById(Long id);
}
