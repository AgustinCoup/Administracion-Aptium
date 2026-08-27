package com.example.features.lavadero.controller.helpers;

import com.example.features.lavadero.model.CicloLavadero;
import com.example.features.lavadero.model.ElementoCicloItem;
import com.example.features.lavadero.model.Lavarropas;
import com.example.features.lavadero.view.helpers.LavarropasItem;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

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
     * @param hayPendientes habilita "Lanzar todos" y "Descartar todos"
     * @param hayActivos    habilita "Finalizar todos"
     */
    public record VistaCiclos(List<ElementoCicloItem> disponibles,
                              List<LavarropasItem> lavarropas,
                              List<VistaCard> cards,
                              boolean hayPendientes,
                              boolean hayActivos) {
    }

    private ConstructorVistaCiclos() {
    }

    /**
     * @param numerosDeCard lavarropas que la pantalla tiene dibujados, en el orden en que
     *                      se van a pintar
     */
    public static VistaCiclos construir(DatosCiclos datos,
                                        Collection<Integer> numerosDeCard,
                                        StagingCiclos staging) {
        // Muta los ítems recibidos y devuelve los que todavía tienen unidades libres
        // (ver la nota de clase de StagingCiclos).
        List<ElementoCicloItem> disponibles = staging.aplicarSobreDisponibles(datos.disponibles());

        return new VistaCiclos(
            disponibles,
            mapearLavarropas(datos),
            armarCards(datos, numerosDeCard, staging),
            staging.hayPendientes(),
            !datos.ciclosActivos().isEmpty()
        );
    }

    private static List<LavarropasItem> mapearLavarropas(DatosCiclos datos) {
        List<LavarropasItem> items = new ArrayList<>();
        for (Lavarropas lavarropas : datos.lavarropas()) {
            CicloLavadero activo = datos.ciclosActivos().get(lavarropas.getNumero());
            items.add(new LavarropasItem(lavarropas.getNumero(), lavarropas.getCapacidadLitros(),
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
