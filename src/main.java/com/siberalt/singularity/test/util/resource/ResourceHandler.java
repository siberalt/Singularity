package com.siberalt.singularity.test.util.resource;

import java.util.function.Supplier;

public class ResourceHandler<T extends AutoCloseable> {
    protected T resource;

    protected final Supplier<T> supplier;

    public ResourceHandler(Supplier<T> supplier) {
        this.supplier = supplier;
    }

    public void close() {
        if (null == resource) {
            return;
        }

        try {
            resource.close();
            resource = null;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public T create() {
        return create(this.supplier);
    }

    public T create(Supplier<T> supplier) {
        if (null != resource) {
            return resource;
        }

        try {
            this.resource = supplier.get();
            Runtime.getRuntime().addShutdownHook(new Thread(this::close));
        } catch (Exception e) {
            if (null != resource) {
                close();
            }
            throw new RuntimeException(e);
        }
        return resource;
    }

    public static <ST extends AutoCloseable> ResourceHandler<ST> newHandler(Supplier<ST> resourceSupplier) {
        return new ResourceHandler<>(resourceSupplier);
    }
}
