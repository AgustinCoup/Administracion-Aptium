package com.example.features.lavadero.controller.helpers;

import com.example.features.lavadero.model.CicloLavadero;
import com.example.features.lavadero.model.ElementoCicloItem;
import com.example.features.lavadero.model.InsumoCatalogo;
import com.example.features.lavadero.model.JabonCatalogo;
import com.example.features.lavadero.model.Lavarropas;

import java.util.List;
import java.util.Map;

/**
 * Todo lo que la pantalla de Ciclos necesita de la base, leído de una sola vez fuera del
 * hilo de la interfaz. Es la mitad "de la base" de la pantalla; la otra mitad —el staging—
 * vive sólo en el hilo de la interfaz y se combina con esto en
 * {@link ConstructorVistaCiclos#construir}.
 *
 * @param ciclosActivos             lavarropas ocupado → su ciclo en curso
 * @param disponibles               elementos clasificados que todavía se pueden repartir
 * @param lavarropas                todos los lavarropas configurados
 * @param itemsPorLavarropasActivo  lavarropas ocupado → lo que hay adentro de su ciclo
 * @param jabones                   catálogo de jabones, o lista vacía si esta carga no lo
 *                                  pidió porque ya estaba en memoria (no cambia en runtime)
 * @param insumos                   catálogo de insumos extra, o lista vacía si esta carga no lo
 *                                  pidió porque ya estaba en memoria (no cambia en runtime).
 *                                  Es el <b>catálogo</b> que alimenta el combo de cada card, no
 *                                  los insumos elegidos: ésos son configuración de card y viven
 *                                  sólo en el hilo de la interfaz
 */
public record DatosCiclos(Map<Integer, CicloLavadero> ciclosActivos,
                          List<ElementoCicloItem> disponibles,
                          List<Lavarropas> lavarropas,
                          Map<Integer, List<ElementoCicloItem>> itemsPorLavarropasActivo,
                          List<JabonCatalogo> jabones,
                          List<InsumoCatalogo> insumos) {
}
