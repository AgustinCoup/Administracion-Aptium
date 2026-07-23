package com.example.app.ui;

import com.example.features.autoclaves.service.AutoclaveService;
import com.example.features.catalogo.service.CatalogoService;
import com.example.features.equipos.ortopedias.service.EquipoService;
import com.example.features.equipos.otros.service.EquipoOtrosService;
import com.example.features.lotes.service.LoteService;
import java.util.Objects;

/**
 * Lee de una sola pasada todo lo que las pantallas necesitan para refrescarse.
 *
 * <p>Clase plana, sin Swing: se ejecuta siempre en un hilo de fondo (ver
 * {@link com.example.ui.common.TareaUI}) y devuelve un {@link DatosRefresco}
 * que después se reparte a todas las pantallas dentro del hilo de UI.
 *
 * <p>Junto con {@link UiCoordinator} es la única pieza de la UI que ve servicios
 * de varias features a la vez; los recibe por constructor desde el
 * {@code AppContext}, como cualquier controller.
 */
public class LectorDatosRefresco {

    private final EquipoService      equipoService;
    private final EquipoOtrosService equipoOtrosService;
    private final AutoclaveService   autoclaveService;
    private final CatalogoService    catalogoService;
    private final LoteService        loteService;

    public LectorDatosRefresco(EquipoService equipoService,
                               EquipoOtrosService equipoOtrosService,
                               AutoclaveService autoclaveService,
                               CatalogoService catalogoService,
                               LoteService loteService) {
        this.equipoService      = Objects.requireNonNull(equipoService, "equipoService");
        this.equipoOtrosService = Objects.requireNonNull(equipoOtrosService, "equipoOtrosService");
        this.autoclaveService   = Objects.requireNonNull(autoclaveService, "autoclaveService");
        this.catalogoService    = Objects.requireNonNull(catalogoService, "catalogoService");
        this.loteService        = Objects.requireNonNull(loteService, "loteService");
    }

    /** Las seis queries del refresco, cada una exactamente una vez. */
    public DatosRefresco leer() {
        return new DatosRefresco(
            equipoService.obtenerTodos(),
            equipoOtrosService.obtenerTodos(),
            autoclaveService.obtenerTodos(),
            catalogoService.obtenerVolumenes(),
            loteService.obtenerLotesActivosPorAutoclave(),
            loteService.obtenerTodosLosLotes()
        );
    }
}
