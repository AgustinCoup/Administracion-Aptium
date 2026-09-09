package com.example.features.lavadero.dao.derivadores;

import com.example.features.equipos.otros.dao.EquipoOtrosDAO;
import com.example.features.equipos.otros.model.EquipoOtros;
import com.example.features.lavadero.model.AccionSalida;
import com.example.features.lavadero.model.SalidaLista;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Crea en el CDE los ingresos "otros" que corresponden a un conjunto de salidas listas.
 *
 * <p>Una sola clase con dos instancias: derivar conservando el cliente original y derivar a
 * nombre de APTIUM se diferencian únicamente en el {@link AsignadorClienteCDE} que reciben.
 * Todo lo demás — armar los ingresos, persistirlos, devolver el mapa de salidas — es idéntico.
 *
 * <p>Persiste con {@link EquipoOtrosDAO#guardar(Connection, EquipoOtros)}, la variante que
 * participa de la transacción del llamador y propaga la {@link SQLException}: si la creación del
 * ingreso falla, la derivación entera se cae y {@code salidas_lavadero} queda intacta.
 */
public final class DerivadorIngresoCDE implements DerivadorSalidas {

    private static final Logger log = LoggerFactory.getLogger(DerivadorIngresoCDE.class);

    private final AccionSalida          accion;
    private final ConstructorIngresoCDE constructor;
    private final AsignadorClienteCDE   asignador;
    private final EquipoOtrosDAO        equipoOtrosDAO;

    public DerivadorIngresoCDE(AccionSalida accion,
                               ConstructorIngresoCDE constructor,
                               AsignadorClienteCDE asignador,
                               EquipoOtrosDAO equipoOtrosDAO) {
        this.accion         = Objects.requireNonNull(accion, "accion no puede ser null");
        this.constructor    = Objects.requireNonNull(constructor, "constructor no puede ser null");
        this.asignador      = Objects.requireNonNull(asignador, "asignador no puede ser null");
        this.equipoOtrosDAO = Objects.requireNonNull(equipoOtrosDAO, "equipoOtrosDAO no puede ser null");
    }

    @Override
    public AccionSalida accion() {
        return accion;
    }

    @Override
    public Map<Integer, Integer> derivar(Connection conn, List<SalidaLista> salidas) throws SQLException {
        IngresosCDE armados = constructor.construir(salidas, asignador);
        Map<Integer, Integer> equipoPorSalida = new LinkedHashMap<>();

        for (EquipoOtros ingreso : armados.ingresos()) {
            int equipoOtrosId = equipoOtrosDAO.guardar(conn, ingreso);
            log.info("Ingreso a CDE desde lavadero ({}): equipo_otros={}, cliente={}, {} elemento(s)",
                     accion, equipoOtrosId, ingreso.getNroCliente(), ingreso.getMateriales().size());
            for (Integer salidaId : armados.salidaIds().get(ingreso)) {
                equipoPorSalida.put(salidaId, equipoOtrosId);
            }
        }
        return equipoPorSalida;
    }
}
