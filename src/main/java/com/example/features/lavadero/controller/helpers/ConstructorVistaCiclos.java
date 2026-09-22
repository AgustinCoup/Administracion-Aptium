package com.example.features.lavadero.controller.helpers;

import com.example.features.lavadero.model.CicloLavadero;
import com.example.features.lavadero.model.ElementoCicloItem;
import com.example.features.lavadero.model.Lavarropas;
import com.example.features.lavadero.view.helpers.LavarropasItem;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Arma qué muestra la pantalla de Ciclos combinando lo leído de la base
 * ({@link DatosCiclos}) con el staging en memoria.
 *
 * <p>Existe para que esa combinación —el descuento de lo staged sobre los disponibles, el
 * mapeo de lavarropas y la decisión activo/staging de cada card— se pueda testear sin EDT
 * ni base, igual que {@code AgrupadorEntregas} y {@code ConstructorMaterialesDisponibles}.
 *
 * <p><b>Corre en el hilo de la interfaz</b>, no en el de fondo: toca {@link StagingCiclos},
 * que es el estado mutable que también leen el arrastre y los diálogos de subdivisión. Lo
 * único que se hace en fondo es traer los {@link DatosCiclos}.
 *
 * <p><b>Los catálogos de {@link DatosCiclos} (jabones, insumos) no pasan por acá</b> y no
 * descartan nada: son lo que alimenta los combos de las cards, y los insumos que el operador ya
 * eligió son configuración de card, no staging. Que una relectura traiga un catálogo nuevo no es
 * motivo para resetear ninguna card — {@code recargar()} / F5 no pisa lo que se está tipeando.
 */
public final class ConstructorVistaCiclos {

    /**
     * Qué muestra una card: los elementos del ciclo en curso, o los pendientes que el
     * operador cargó en el staging.
     *
     * @param cicloActivoId id del ciclo en curso, o {@code null} si el lavarropas está libre
     * @param fracciones    denominador de la columna {@code Fracción} ({@code 1/3}); vacío en
     *                      las cards activas, cuyos ítems vienen de la base y no del staging
     */
    public record VistaCard(int lavarropasNumero,
                            Integer cicloActivoId,
                            List<ElementoCicloItem> items,
                            Map<Integer, Integer> fracciones) {

        public boolean esActivo() {
            return cicloActivoId != null;
        }
    }

    /**
     * La pantalla entera lista para volcar.
     *
     * <p><b>El staging descartado viaja en dos listas y no en una.</b> Los dos motivos son
     * distintos para el operador —"otro usuario lanzó un ciclo ahí" y "ese lavarropas se retiró
     * del lavadero"— y tienen su propio {@code Mensajes.*}. Un solo campo obligaría a un cartel
     * que cubra los dos casos, y ése diría de uno de los dos algo que es falso; un cartel que
     * miente entrena al operador a apretar "Sí" sin leer y desactiva también los avisos
     * verdaderos.</p>
     *
     * @param hayPendientes                 habilita "Lanzar todos" y "Descartar todos"
     * @param hayActivos                    habilita "Finalizar todos"
     * @param stagingDescartadoPorOcupacion lavarropas que perdieron su staging porque volvieron de
     *                                      la base con un ciclo activo, y los que lo perdieron de
     *                                      arrastre por compartir un equipo repartido con ellos
     * @param stagingDescartadoPorBaja      ídem, pero porque el lavarropas dejó de estar entre los
     *                                      que la pantalla dibuja: lo dieron de baja. Los dos son
     *                                      vacíos en el caso normal; cuando no lo están hay que
     *                                      <b>avisarlo</b>, porque es trabajo del operador que
     *                                      desaparece de la pantalla sin que él haya hecho nada
     *                                      (ver {@link #descartarStagingNoLanzable})
     */
    public record VistaCiclos(List<ElementoCicloItem> disponibles,
                              List<LavarropasItem> lavarropas,
                              List<VistaCard> cards,
                              boolean hayPendientes,
                              boolean hayActivos,
                              List<Integer> stagingDescartadoPorOcupacion,
                              List<Integer> stagingDescartadoPorBaja) {
    }

    private ConstructorVistaCiclos() {
    }

    /**
     * @param numerosDeCard lavarropas que la pantalla <b>va a</b> dibujar después de esta
     *                      lectura, en el orden en que se van a pintar: lo que trajo
     *                      {@code obtenerDibujables()}, no lo que la grilla tiene puesto ahora.
     *                      Es también el conjunto contra el que se decide el descarte por baja —
     *                      un número que no está acá ya no tiene dónde lanzarse
     */
    public static VistaCiclos construir(DatosCiclos datos,
                                        Collection<Integer> numerosDeCard,
                                        StagingCiclos staging) {
        Descartes descartes = descartarStagingNoLanzable(datos, numerosDeCard, staging);

        // Muta los ítems recibidos y devuelve los que todavía tienen unidades libres
        // (ver la nota de clase de StagingCiclos).
        List<ElementoCicloItem> disponibles = staging.aplicarSobreDisponibles(datos.disponibles());

        return new VistaCiclos(
            disponibles,
            mapearLavarropas(datos),
            armarCards(datos, numerosDeCard, staging),
            staging.hayPendientes(),
            !datos.ciclosActivos().isEmpty(),
            descartes.porOcupacion(),
            descartes.porBaja()
        );
    }

    /** Lo descartado, separado por motivo: cada uno tiene su propio cartel. */
    private record Descartes(List<Integer> porOcupacion, List<Integer> porBaja) {
    }

    /**
     * Descarta el staging que ya no se puede lanzar, por cualquiera de los dos motivos que
     * existen: el lavarropas volvió de la base <b>ocupado</b>, o <b>ya no está entre los que la
     * pantalla dibuja</b> porque lo dieron de baja.
     *
     * <p>Los dos casos son el mismo problema —ropa asignada a un lavarropas al que ya no se le
     * puede lanzar nada— y por eso comparten el tratamiento: se vacía el staging de esa card y la
     * ropa vuelve a disponibles. Lo que <b>no</b> comparten es el aviso, y por eso se devuelven en
     * dos listas separadas (ver {@link VistaCiclos}).</p>
     *
     * <p>Un lavarropas se puede ocupar entre que el operador le arrastró ropa y el refresco
     * siguiente: otro operador lanzó su ciclo. La card ya deja de mostrar ese staging
     * ({@link #armarCards} pinta los ítems del ciclo activo), pero el staging seguía vivo, y
     * "Lanzar todos" lo mandaba igual — un segundo ciclo ACTIVO en el mismo lavarropas, que la
     * pantalla ni siquiera puede mostrar (ver la guarda de
     * {@code CicloLavaderoDAO.SQL_CICLO_ACTIVO_DE_LAVARROPAS}, que ahora lo rechaza en la base).
     * Con la baja pasa lo análogo: {@code exigirLavarropasActivos} rechaza la tanda entera, así
     * que ese staging sólo puede terminar en un choque.</p>
     *
     * <p>Va <b>antes</b> de descontar los disponibles: así esa ropa deja de estar descontada y
     * vuelve a aparecer en la tabla, que es donde el operador la puede volver a repartir. No se
     * pierde nada — lo único que se descarta es la asignación a un lavarropas que ya no sirve.
     * Por el mismo motivo corre <b>antes</b> de que {@code CiclosController} reconstruya la
     * grilla: si la card desaparece primero, el staging queda huérfano y la ropa no vuelve.</p>
     *
     * <p>Si lo que había ahí era la fracción de un equipo repartido se deshace la subdivisión
     * <b>entera</b>, en todos los lavarropas — incluidos los que siguen libres. Es la misma regla
     * que {@code StagingCiclos.quitar} aplica a la devolución manual, y por el mismo motivo: dejar
     * 2 de 3 fracciones cambia en silencio el reparto que el operador armó. Justamente porque el
     * daño se propaga más allá del lavarropas afectado, esta baja <b>no puede ser silenciosa</b>:
     * se devuelven los lavarropas afectados para que {@code CiclosController} los avise.</p>
     *
     * <p>La ocupación se procesa primero, y eso alcanza para que ningún lavarropas salga en las
     * dos listas: el que ya perdió su staging ahí llega sin pendientes a la pasada de baja. No es
     * un caso que se pueda dar por sí solo —{@code obtenerDibujables()} incluye a los inactivos
     * con ciclo abierto justamente para que tengan card—, pero sí por arrastre de una fracción.</p>
     *
     * @param numerosVigentes lavarropas que la pantalla va a dibujar tras esta lectura
     * @return los lavarropas que efectivamente perdieron staging, en orden ascendente y separados
     *         por motivo; los dos vacíos si no hubo nada que descartar, que es el caso normal
     */
    private static Descartes descartarStagingNoLanzable(DatosCiclos datos,
                                                        Collection<Integer> numerosVigentes,
                                                        StagingCiclos staging) {
        Set<Integer> porOcupacion = new TreeSet<>();
        for (int ocupado : datos.ciclosActivos().keySet()) {
            vaciarConSuReparto(ocupado, staging, porOcupacion);
        }

        Set<Integer> vigentes = new HashSet<>(numerosVigentes);
        Set<Integer> porBaja = new TreeSet<>();
        for (int cargado : staging.lavarropasConPendientes()) {
            if (vigentes.contains(cargado)) continue;
            vaciarConSuReparto(cargado, staging, porBaja);
        }

        return new Descartes(List.copyOf(porOcupacion), List.copyOf(porBaja));
    }

    /**
     * Vacía el staging de un lavarropas y, si lo que había era la fracción de un equipo
     * repartido, deshace el reparto entero. Suma a {@code afectados} el lavarropas y todos los
     * que el reparto se lleve puestos; no suma nada si no había nada que descartar.
     */
    private static void vaciarConSuReparto(int numero, StagingCiclos staging,
                                           Set<Integer> afectados) {
        List<ElementoCicloItem> pendientes = staging.pendientesDe(numero);
        if (pendientes.isEmpty()) return;
        afectados.add(numero);
        for (ElementoCicloItem item : pendientes) {
            if (item.isEquipo() && item.getInstanciaId() != null) {
                afectados.addAll(staging.lavarropasConFraccionesDe(item.getInstanciaId()));
                staging.quitarInstanciaEquipo(item.getInstanciaId());
            }
        }
        staging.limpiarLavarropas(numero);
    }

    private static List<LavarropasItem> mapearLavarropas(DatosCiclos datos) {
        List<LavarropasItem> items = new ArrayList<>();
        for (Lavarropas lavarropas : datos.lavarropas()) {
            CicloLavadero activo = datos.ciclosActivos().get(lavarropas.getNumero());
            items.add(new LavarropasItem(lavarropas.getNumero(),
                                         activo != null, activo != null ? activo.getId() : null));
        }
        return items;
    }

    private static List<VistaCard> armarCards(DatosCiclos datos,
                                              Collection<Integer> numerosDeCard,
                                              StagingCiclos staging) {
        Map<Integer, Integer> fracciones = staging.fraccionesPorInstancia();
        List<VistaCard> cards = new ArrayList<>();
        for (int numero : numerosDeCard) {
            CicloLavadero activo = datos.ciclosActivos().get(numero);
            if (activo != null) {
                cards.add(new VistaCard(numero, activo.getId(),
                    datos.itemsPorLavarropasActivo().getOrDefault(numero, List.of()), Map.of()));
            } else {
                cards.add(new VistaCard(numero, null, staging.pendientesDe(numero), fracciones));
            }
        }
        return cards;
    }
}
