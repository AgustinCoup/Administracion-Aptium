package com.example.app.ui;

import com.example.features.equipos.ortopedias.service.EquipoService;
import com.example.features.equipos.otros.service.EquipoOtrosService;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Lee el histórico completo de equipos para las pantallas de consulta.
 *
 * <p>Clase plana, sin Swing: corre en un hilo de fondo y devuelve un
 * {@link HistorialEquipos}. Son las dos queries más caras de la aplicación, y por
 * eso solo se disparan al abrir {@code Ver Equipos} o {@code Estado de procesos},
 * no en cada guardado.
 */
public class LectorHistorialEquipos implements Supplier<HistorialEquipos> {

    private final EquipoService      equipoService;
    private final EquipoOtrosService equipoOtrosService;

    public LectorHistorialEquipos(EquipoService equipoService,
                                  EquipoOtrosService equipoOtrosService) {
        this.equipoService      = Objects.requireNonNull(equipoService, "equipoService");
        this.equipoOtrosService = Objects.requireNonNull(equipoOtrosService, "equipoOtrosService");
    }

    @Override
    public HistorialEquipos get() {
        return new HistorialEquipos(
            equipoService.obtenerTodos(),
            equipoOtrosService.obtenerTodos()
        );
    }
}
