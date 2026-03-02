package com.example.common.util;

import java.util.List;

public abstract class AbstractFilterController<T> {

    private List<T> cache = List.of();

    protected void recargarCache(List<T> datos) {
        this.cache = datos != null ? datos : List.of();
        aplicarFiltros();
    }

    protected List<T> getCache() {
        return cache;
    }

    protected abstract void aplicarFiltros();
}
