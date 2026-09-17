package com.example.features.lavadero.service;

import com.example.common.exception.ValidationException;
import com.example.common.paginacion.CriteriosPagina;
import com.example.common.paginacion.Pagina;
import com.example.features.lavadero.dao.HistorialLavaderoDAO;
import com.example.features.lavadero.model.FiltroHistorial;
import com.example.features.lavadero.model.IngresoHistorial;
import com.example.features.lavadero.model.LineaHistorial;

import java.util.List;

/**
 * Capa de validación sobre {@link HistorialLavaderoDAO}. Sólo valida y delega, igual que
 * {@link SalidaLavaderoService}: cero JDBC acá.
 *
 * <p>El historial es de sólo lectura. {@link #obtenerPagina(FiltroHistorial, CriteriosPagina)}
 * trae una página de la tabla maestra con los filtros ya resueltos en SQL —es lo que usa la
 * pantalla—; {@link #obtenerHistorial()} trae la maestra entera sin filtrar, y sobrevive como
 * implementación de referencia del test de equivalencia del DAO; {@link #obtenerDetalle(int)} lee
 * la trazabilidad de un solo ingreso, bajo demanda.</p>
 */
public class HistorialLavaderoService {

    private final HistorialLavaderoDAO dao;

    public HistorialLavaderoService(HistorialLavaderoDAO dao) {
        if (dao == null) throw new IllegalArgumentException("HistorialLavaderoDAO no puede ser nulo");
        this.dao = dao;
    }

    /** Todos los ingresos, del más reciente al más viejo. El filtrado es en memoria. */
    public List<IngresoHistorial> obtenerHistorial() {
        return dao.obtenerHistorial();
    }

    /**
     * Una página del historial con los filtros ya resueltos en SQL, más el total que matchea.
     *
     * @param filtro    los cinco filtros de la pantalla; {@code null} no es válido —sin filtros se
     *                  pasa {@link FiltroHistorial#sinFiltros()}, que dice lo mismo explícitamente
     * @param criterios qué página y de qué tamaño
     */
    public Pagina<IngresoHistorial> obtenerPagina(FiltroHistorial filtro, CriteriosPagina criterios) {
        validar(filtro, criterios);
        return dao.obtenerPagina(filtro, criterios);
    }

    /**
     * La misma página, con el total ya sabido: <b>no vuelve a contar</b>.
     *
     * <p>El total sólo cambia cuando cambian los filtros, así que navegar entre páginas del mismo
     * filtro no necesita un {@code COUNT(*)} nuevo. El total tiene que venir del <b>mismo</b>
     * filtro; con otro, la UI dibujaría pestañas que no existen.</p>
     */
    public Pagina<IngresoHistorial> obtenerPagina(FiltroHistorial filtro, CriteriosPagina criterios,
                                                  long totalConocido) {
        validar(filtro, criterios);
        ValidationException.builder()
            .addErrorIf(totalConocido < 0, "El total conocido no puede ser negativo.")
            .throwIfHasErrors();
        return dao.obtenerPagina(filtro, criterios, totalConocido);
    }

    /** Cuántos ingresos matchean el filtro, con el mismo {@code WHERE} que la página. */
    public long contar(FiltroHistorial filtro) {
        ValidationException.builder()
            .addErrorIf(filtro == null, "El filtro del historial no puede ser nulo.")
            .throwIfHasErrors();
        return dao.contarHistorial(filtro);
    }

    private static void validar(FiltroHistorial filtro, CriteriosPagina criterios) {
        ValidationException.builder()
            .addErrorIf(filtro == null,    "El filtro del historial no puede ser nulo.")
            .addErrorIf(criterios == null, "Los criterios de paginación no pueden ser nulos.")
            .throwIfHasErrors();
    }

    /** Trazabilidad completa de un ingreso. */
    public List<LineaHistorial> obtenerDetalle(int ingresoId) {
        ValidationException.builder()
            .addErrorIf(ingresoId <= 0, "El id del ingreso tiene que ser mayor que cero.")
            .throwIfHasErrors();
        return dao.findDetalle(ingresoId);
    }
}
