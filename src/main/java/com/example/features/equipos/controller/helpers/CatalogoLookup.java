package com.example.features.equipos.controller.helpers;

@FunctionalInterface
public interface CatalogoLookup {
    boolean existeCodigo(int codigo);
}
