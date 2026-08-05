package com.example.app.ui;

import com.example.features.autoclaves.service.AutoclaveService;
import com.example.features.lotes.service.LoteService;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Lee el histórico completo de lotes para la pantalla de consulta.
 *
 * <p>Clase plana, sin Swing: corre en un hilo de fondo y devuelve un
 * {@link HistorialLotes}. Se dispara al abrir {@code Ver Lotes}, no en cada
 * guardado: antes cada lote lanzado o finalizado releía toda la grilla aunque
 * la pantalla estuviera oculta.
 */
public class LectorHistorialLotes implements Supplier<HistorialLotes> {

    private final AutoclaveService autoclaveService;
    private final LoteService      loteService;

    public LectorHistorialLotes(AutoclaveService autoclaveService, LoteService loteService) {
        this.autoclaveService = Objects.requireNonNull(autoclaveService, "autoclaveService");
        this.loteService      = Objects.requireNonNull(loteService, "loteService");
    }

    @Override
    public HistorialLotes get() {
        return new HistorialLotes(
            autoclaveService.obtenerTodos(),
            loteService.obtenerTodosLosLotes()
        );
    }
}
