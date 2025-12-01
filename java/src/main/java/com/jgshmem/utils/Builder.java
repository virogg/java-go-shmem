package com.jgshmem.utils;

public interface Builder<T> {
    public T newInstance();

    public static <T> Builder<T> createBuilder(final Class<T> cls) {
        return new Builder<T>() {
            @Override
            public T newInstance() {
                try {
                    return cls.getDeclaredConstructor().newInstance();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        };
    }
}