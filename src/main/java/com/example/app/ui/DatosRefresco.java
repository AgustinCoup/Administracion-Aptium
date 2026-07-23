package com.example.app.ui;

import com.example.features.autoclaves.model.Autoclave;
import com.example.features.equipos.ortopedias.model.Equipo;
import com.example.features.equipos.otros.model.EquipoOtros;
import com.example.features.lotes.model.Lote;
import java.util.List;
import java.util.Map;

/**
 * Foto de la base de datos que alimenta a todas las pantallas en un refresco.
 *
 * <p>Antes cada pantalla leía lo suyo por su cuenta: 13 queries en serie por
 * guardado, con {@code equipos} y {@code equipos_otros} repetidos cuatro veces
 * cada uno, y cada pantalla viendo un instante distinto de la base. Este record
 * es el resultado de leer las seis queries una sola vez, para que todas pinten
 * el mismo estado.
 *
 * <p>Es inmutable de raíz para adentro: se construye en un hilo de fondo y se
 * consume en el hilo de UI. Los modelos que contiene se tratan como de solo
 * lectura — quien reciba este record puede transformar, no mutar.
 *
 * @param equipos           todos los equipos de ortopedia
 * @param equiposOtros      todos los equipos "otros"
 * @param autoclaves        autoclaves configurados
 * @param volumenesCatalogo volumen unitario por código de catálogo
 * @param lotesActivos      lote en curso por nombre de autoclave
 * @param todosLosLotes     histórico completo de lotes
 */
public record DatosRefresco(
    List<Equipo>         equipos,
    List<EquipoOtros>    equiposOtros,
    List<Autoclave>      autoclaves,
    Map<Integer,Integer> volumenesCatalogo,
    Map<String,Lote>     lotesActivos,
    List<Lote>           todosLosLotes
) {

    public DatosRefresco {
        equipos           = List.copyOf(equipos);
        equiposOtros      = List.copyOf(equiposOtros);
        autoclaves        = List.copyOf(autoclaves);
        volumenesCatalogo = Map.copyOf(volumenesCatalogo);
        lotesActivos      = Map.copyOf(lotesActivos);
        todosLosLotes     = List.copyOf(todosLosLotes);
    }

    /** Snapshot vacío, para el primer pintado antes de que llegue la lectura real. */
    public static DatosRefresco vacio() {
        return new DatosRefresco(List.of(), List.of(), List.of(), Map.of(), Map.of(), List.of());
    }
}
