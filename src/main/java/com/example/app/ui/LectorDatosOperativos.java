package com.example.app.ui;

import com.example.features.autoclaves.service.AutoclaveService;
import com.example.features.catalogo.service.CatalogoService;
import com.example.features.equipos.ortopedias.service.EquipoService;
import com.example.features.equipos.otros.service.EquipoOtrosService;
import com.example.features.lotes.service.LoteService;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Lee de una sola pasada la cola de trabajo que alimenta a las pantallas operativas.
 *
 * <p>Clase plana, sin Swing: se ejecuta siempre en un hilo de fondo (ver
 * {@link com.example.ui.common.TareaUI}) y devuelve un {@link DatosOperativos}
 * que después se reparte a todas las pantallas dentro del hilo de UI.
 *
 * <p>Las cinco queries son el costo fijo de cada guardado, y las dos pesadas
 * ({@code obtenerActivos}) están acotadas a la cola activa a propósito.
 *
 * <p>Junto con {@link UiCoordinator} es una de las pocas piezas de la UI que ve
 * servicios de varias features a la vez; los recibe por constructor desde el
 * {@code AppContext}, como cualquier controller.
 */
public class LectorDatosOperativos implements Supplier<DatosOperativos> {

    private final EquipoService      equipoService;
    private final EquipoOtrosService equipoOtrosService;
    private final AutoclaveService   autoclaveService;
    private final CatalogoService    catalogoService;
    private final LoteService        loteService;

    public LectorDatosOperativos(EquipoService equipoService,
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

    /** Las cinco queries del refresco operativo, cada una exactamente una vez. */
    @Override
    public DatosOperativos get() {
        return new DatosOperativos(
            equipoService.obtenerActivos(),
            equipoOtrosService.obtenerActivos(),
            autoclaveService.obtenerTodos(),
            catalogoService.obtenerVolumenes(),
            loteService.obtenerLotesActivosPorAutoclave()
        );
    }
}
