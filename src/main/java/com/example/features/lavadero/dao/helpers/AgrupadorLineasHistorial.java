package com.example.features.lavadero.dao.helpers;

import com.example.features.lavadero.model.LineaHistorial;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Agrupa las fracciones de un {@code Equipo*} repartido (una fila por lavarropas) en una sola
 * línea del detalle del historial. Sin dependencias de JDBC ni Swing — se testea sola, mismo
 * patrón que {@link AgrupadorInstanciasSalida}.
 *
 * <p><b>A diferencia de {@link AgrupadorInstanciasSalida}, no descarta nada.</b> Aquel filtra
 * las instancias incompletas y las ya marcadas como Listo porque su pantalla ofrece acciones
 * sobre lo que muestra; el historial es de sólo lectura y esas dos son justamente las
 * situaciones que tiene que contar — una instancia a medio lavar sale con
 * {@code fechaLavado == null}, y una ya derivada conserva su destino. Por eso es una clase
 * aparte y no un booleano que invierta la regla de la otra.</p>
 */
public final class AgrupadorLineasHistorial {

    /**
     * Una línea por instancia, ordenadas por nombre de elemento. La cantidad es siempre 1:
     * un equipo repartido en N lavarropas es un equipo, no N.
     */
    public List<LineaHistorial> agrupar(List<FilaHistorialCruda> filas) {
        Map<Integer, List<FilaHistorialCruda>> porInstancia = filas.stream()
            .collect(Collectors.groupingBy(FilaHistorialCruda::instanciaEquipoId));
        List<LineaHistorial> resultado = new ArrayList<>();
        for (List<FilaHistorialCruda> grupo : porInstancia.values()) {
            FilaHistorialCruda primera = grupo.get(0);
            resultado.add(new LineaHistorial(
                primera.elementoNombre(),
                1,
                TextoLavarropas.de(grupo, FilaHistorialCruda::lavarropasNumero),
                primera.totalPartes(),
                fechaLavado(grupo),
                primera.fechaListo(),
                primera.destino()));
        }
        resultado.sort(Comparator.comparing(LineaHistorial::elementoNombre));
        return resultado;
    }

    /**
     * Cuándo terminó de lavarse la instancia entera: la última de sus fracciones. Mientras
     * alguna siga en un ciclo activo el equipo no está lavado, así que devuelve {@code null}
     * en vez de la fecha de las que sí terminaron.
     */
    private LocalDateTime fechaLavado(List<FilaHistorialCruda> grupo) {
        if (grupo.stream().anyMatch(f -> f.fechaFinCiclo() == null)) return null;
        return grupo.stream().map(FilaHistorialCruda::fechaFinCiclo)
            .max(Comparator.naturalOrder()).orElse(null);
    }
}
