package com.example.features.equipos.ortopedias.controller.helpers;

@FunctionalInterface
public interface CatalogoLookup {
    boolean existeCodigo(int codigo);
}
