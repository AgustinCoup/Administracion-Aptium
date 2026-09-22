package com.example.features.lavadero.controller.helpers;

import com.example.features.lavadero.model.CicloLavadero;
import com.example.features.lavadero.model.ElementoCicloItem;
import com.example.features.lavadero.model.InsumoCatalogo;
import com.example.features.lavadero.model.JabonCatalogo;
import com.example.features.lavadero.model.Lavarropas;
import com.example.features.lavadero.model.TipoLavado;

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
 * @param lavarropas                los lavarropas que la pantalla dibuja: los activos <b>más</b>
 *                                  los inactivos que todavía tienen un ciclo sin finalizar, que
 *                                  necesitan card para poder finalizarlo. <b>No</b> es "los
 *                                  activos": un ciclo abierto sin card se queda sin botón
 *                                  Finalizar, nunca cierra, y su ropa desaparece de Disponibles
 *                                  <i>y</i> de Salidas (ver {@code LavarropasDAO.obtenerDibujables})
 * @param itemsPorLavarropasActivo  lavarropas ocupado → lo que hay adentro de su ciclo
 * @param jabones                   catálogo de jabones <b>activos</b>, para el combo de cada card.
 *                                  Se relee en cada carga: los catálogos se editan desde Ajustes,
 *                                  y leerlos una sola vez por sesión dejaba a Ciclos mostrando uno
 *                                  viejo hasta reiniciar la app
 * @param insumos                   catálogo de insumos extra activos, ídem. Es el <b>catálogo</b>
 *                                  que alimenta el combo de cada card, no los insumos elegidos:
 *                                  ésos son configuración de card y viven sólo en el hilo de la
 *                                  interfaz
 * @param defaultsJabon             jabón por defecto de cada tipo de lavado, para la carga
 *                                  automática. Un tipo sin default no está en el mapa, y el jabón
 *                                  que sí está <b>puede venir dado de baja</b>: qué hacer con eso
 *                                  lo decide {@link SelectorJabonAutomatico}, no la consulta
 */
public record DatosCiclos(Map<Integer, CicloLavadero> ciclosActivos,
                          List<ElementoCicloItem> disponibles,
                          List<Lavarropas> lavarropas,
                          Map<Integer, List<ElementoCicloItem>> itemsPorLavarropasActivo,
                          List<JabonCatalogo> jabones,
                          List<InsumoCatalogo> insumos,
                          Map<TipoLavado, JabonCatalogo> defaultsJabon) {
}
