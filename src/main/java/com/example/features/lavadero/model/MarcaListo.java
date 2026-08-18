package com.example.features.lavadero.model;

/** Una fila de la selección a marcar como Listo. Une el qué con el cuánto. */
public record MarcaListo(ElementoLavadoPendiente item, int cantidad) { }
