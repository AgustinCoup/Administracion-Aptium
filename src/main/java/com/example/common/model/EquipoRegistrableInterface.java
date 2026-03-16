package com.example.common.model;

import java.util.List;

import com.example.features.equipos.ortopedias.model.EstadoEquipo;

/**
 * Interfaz común para todos los equipos que participan en el flujo de estados.
 *
 * Permite que {@link com.example.features.equipos.ortopedias.controller.RegistrarEstadoController},
 * {@link com.example.features.equipos.ortopedias.view.helpers.EquipoTableModel} y demás componentes
 * compartidos operen de forma uniforme sobre equipos de ortopedia y de "otros".
 *
 * Implementaciones:
 * - {@link com.example.features.equipos.ortopedias.model.Equipo} (ortopedia)
 * - {@link com.example.features.equipos.otros.model.otros.model.EquipoOtros}
 */
public interface EquipoRegistrableInterface {

    /**
     * Discriminador de tipo, usado por el controller para despachar al servicio correcto
     * al confirmar movimientos pendientes.
     */
    enum TipoEquipo { ORTOPEDIA, OTROS }

    TipoEquipo getTipo();

    // ── Identificación ─────────────────────────────────────────────────────────

    Integer getId();
    String getClienteNombre();
    int getNroCliente();

    // ── Columna secundaria para la tabla (institución / vacío) ─────────────────

    /**
     * Texto que se muestra en la segunda columna de la tabla de equipos.
     * Para ortopedia devuelve el nombre de la institución;
     * para "otros" devuelve una cadena vacía.
     */
    String getDescripcionSecundaria();

    // ── Estado ─────────────────────────────────────────────────────────────────

    EstadoEquipo getEstado();
    void setEstado(EstadoEquipo estado);

    boolean isRequiereLavado();
    boolean isRequiereEmpaque();

    /**
     * Estado calculado a partir del material más atrasado.
     * Equivale al estado visible en la UI.
     */
    EstadoEquipo calcularEstado();

    /**
     * Siguiente estado lógico para el estadoActual, respetando
     * la configuración de lavado/empaque del equipo.
     */
    EstadoEquipo getSiguienteEstado(EstadoEquipo estadoActual);

    // ── Materiales ─────────────────────────────────────────────────────────────

    /**
     * Lista de materiales para uso en el flujo de registrar-estado.
     * Retorna una vista no modificable de la lista interna.
     *
     * Nota: los callers internos de cada clase concreta (DAO, ConstructorEquipo, etc.)
     * siguen usando sus propios getters tipados para no necesitar casts.
     */
    List<MaterialRegistrableInterface> getMaterialesRegistrables();

    /**
     * Aplica un movimiento de subcantidad en memoria (preview).
     * No persiste en BD; el controller llama a este método para reflejar
     * visualmente el cambio antes de confirmar.
     *
     * Cada implementación es responsable de hacer el cast interno apropiado.
     */
    void aplicarMovimientoPreview(MaterialRegistrableInterface material, int cantidad,
                                  EstadoEquipo estadoDestino);
}