package com.example.features.lavadero.dao.helpers;

import com.example.features.lavadero.model.ElementoLavadoPendiente;
import com.example.features.lavadero.model.SalidaLista;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;

/**
 * Agrupa filas crudas de fracciones de equipo (una por lavarropas) en una sola fila lógica
 * por instancia, para las dos tablas de Salidas. Sin dependencias de JDBC ni Swing — se
 * testea sola, mismo patrón que {@code StagingCiclos}.
 *
 * <p>La agregación se hace acá y no con {@code GROUP_CONCAT}/{@code STRING_AGG} en SQL para no
 * introducir una función de texto que se comporte distinto entre H2 y MySQL: cada fila cruda
 * se lee tal cual y se arma el grupo en memoria.</p>
 */
public final class AgrupadorInstanciasSalida {

    /**
     * Sólo las instancias completas (todas sus partes presentes y con ciclo finalizado) y
     * todavía sin marcar como Listo.
     */
    public List<ElementoLavadoPendiente> agruparPendientes(List<FilaInstanciaEquipo> filas) {
        Map<Integer, List<FilaInstanciaEquipo>> porInstancia = filas.stream()
            .collect(Collectors.groupingBy(FilaInstanciaEquipo::instanciaEquipoId));
        List<ElementoLavadoPendiente> resultado = new ArrayList<>();
        for (List<FilaInstanciaEquipo> grupo : porInstancia.values()) {
            FilaInstanciaEquipo primera = grupo.get(0);
            boolean completa = grupo.size() == primera.totalPartes()
                && grupo.stream().allMatch(f -> f.fechaFinCiclo() != null);
            boolean yaMarcada = grupo.stream().anyMatch(f -> f.cantidadYaMarcada() > 0);
            if (!completa || yaMarcada) continue;
            resultado.add(new ElementoLavadoPendiente(
                null, primera.instanciaEquipoId(), lavarropasTexto(grupo, FilaInstanciaEquipo::lavarropasNumero),
                primera.ingresoId(), primera.clienteId(), primera.clienteNombre(),
                primera.elementoNombre(), 1, 0,
                grupo.stream().map(FilaInstanciaEquipo::fechaFinCiclo).max(Comparator.naturalOrder()).orElse(null)));
        }
        return resultado;
    }

    /** Simétrico de {@link #agruparPendientes(List)} para el lado ya marcado Listo, sin destino. */
    public List<SalidaLista> agruparListas(List<FilaInstanciaSalidaLista> filas) {
        Map<Integer, List<FilaInstanciaSalidaLista>> porSalida = filas.stream()
            .collect(Collectors.groupingBy(FilaInstanciaSalidaLista::salidaId));
        List<SalidaLista> resultado = new ArrayList<>();
        for (List<FilaInstanciaSalidaLista> grupo : porSalida.values()) {
            FilaInstanciaSalidaLista primera = grupo.get(0);
            resultado.add(new SalidaLista(
                primera.salidaId(), null, primera.instanciaEquipoId(),
                lavarropasTexto(grupo, FilaInstanciaSalidaLista::lavarropasNumero),
                primera.ingresoId(), primera.clienteId(), primera.clienteNombre(),
                primera.elementoNombre(), primera.cantidad(),
                grupo.stream().map(FilaInstanciaSalidaLista::fechaFinCiclo).max(Comparator.naturalOrder()).orElse(null),
                primera.fechaListo()));
        }
        return resultado;
    }

    private static <T> String lavarropasTexto(List<T> grupo, ToIntFunction<T> numero) {
        return grupo.stream().mapToInt(numero).distinct().sorted()
            .mapToObj(String::valueOf).collect(Collectors.joining(", "));
    }
}
