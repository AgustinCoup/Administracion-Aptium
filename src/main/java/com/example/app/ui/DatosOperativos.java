package com.example.app.ui;

import com.example.features.autoclaves.model.Autoclave;
import com.example.features.equipos.ortopedias.model.Equipo;
import com.example.features.equipos.otros.model.EquipoOtros;
import com.example.features.lotes.model.Lote;
import java.util.List;
import java.util.Map;

/**
 * La cola de trabajo: lo que hace falta para operar, y nada más.
 *
 * <p>Es el snapshot que se relee en <b>cada guardado</b>, así que su costo se paga
 * todo el tiempo. Por eso {@code equipos} y {@code equiposOtros} traen solo los
 * <b>no entregados</b> — el corte lo hace el {@code WHERE} de
 * {@code obtenerActivos()}, no un filtro en Java, para que el histórico ni siquiera
 * salga de la base. Sin eso el trabajo por guardado crece con el volumen acumulado
 * de la empresa, que es exactamente lo que no tiene por qué crecer.
 *
 * <p>El histórico completo vive en {@link HistorialEquipos} e {@link HistorialLotes},
 * que se leen solo cuando el usuario abre una pantalla de consulta.
 *
 * <p>Es inmutable de raíz para adentro: se construye en un hilo de fondo y se
 * consume en el hilo de UI. Los modelos que contiene se tratan como de solo
 * lectura — quien reciba este record puede transformar, no mutar.
 *
 * @param equipos           equipos de ortopedia con algo sin entregar
 * @param equiposOtros      equipos "otros" con algo sin entregar
 * @param autoclaves        autoclaves configurados
 * @param volumenesCatalogo volumen unitario por código de catálogo
 * @param lotesActivos      lote en curso por nombre de autoclave
 */
public record DatosOperativos(
    List<Equipo>         equipos,
    List<EquipoOtros>    equiposOtros,
    List<Autoclave>      autoclaves,
    Map<Integer,Integer> volumenesCatalogo,
    Map<String,Lote>     lotesActivos
) {

    public DatosOperativos {
        equipos           = List.copyOf(equipos);
        equiposOtros      = List.copyOf(equiposOtros);
        autoclaves        = List.copyOf(autoclaves);
        volumenesCatalogo = Map.copyOf(volumenesCatalogo);
        lotesActivos      = Map.copyOf(lotesActivos);
    }

    /** Snapshot vacío, para el primer pintado antes de que llegue la lectura real. */
    public static DatosOperativos vacio() {
        return new DatosOperativos(List.of(), List.of(), List.of(), Map.of(), Map.of());
    }
}
