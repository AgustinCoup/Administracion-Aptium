package com.example.features.lavadero.controller.helpers;

import com.example.features.lavadero.model.ConfiguracionCopiada;
import com.example.features.lavadero.model.InsumoCatalogo;
import com.example.features.lavadero.model.JabonCatalogo;

import java.util.ArrayList;
import java.util.List;

/**
 * Filtra una {@link ConfiguracionCopiada} contra los catálogos vigentes al momento de pegar.
 *
 * <p>El filtrado vive acá y no en {@code LavarropasCard}: el controller es quien conoce los
 * catálogos vigentes de la última lectura (jabones y insumos activos), la card no. Es lógica de
 * negocio sin Swing, testeable en aislamiento, la misma regla que siguen
 * {@code ConstructorVistaCiclos} y {@code SelectorJabonAutomatico}.</p>
 *
 * <p>La comparación es siempre por id, nunca por referencia ni por {@code equals}: el jabón y los
 * insumos pegados vienen de una lectura anterior del catálogo, y una lectura nueva puede traer
 * otras instancias para los mismos ítems.</p>
 */
public final class DepuradorPegado {

    /**
     * @param depurada la configuración sin los ítems dados de baja
     * @param omitidos nombres de lo que se quitó, en el orden en que se detectó, para el cartel
     */
    public record Resultado(ConfiguracionCopiada depurada, List<String> omitidos) {}

    private DepuradorPegado() {}

    public static Resultado depurar(ConfiguracionCopiada original,
                                     List<JabonCatalogo> jabonesActivos,
                                     List<InsumoCatalogo> insumosActivos) {
        List<String> omitidos = new ArrayList<>();

        JabonCatalogo jabonOriginal = original.jabon();
        JabonCatalogo jabon = jabonOriginal;
        if (jabonOriginal != null
                && jabonesActivos.stream().noneMatch(j -> j.getId() == jabonOriginal.getId())) {
            omitidos.add(jabonOriginal.getNombre());
            jabon = null;
        }

        List<InsumoCatalogo> insumos = new ArrayList<>();
        for (InsumoCatalogo insumo : original.insumos()) {
            boolean activo = insumosActivos.stream().anyMatch(a -> a.id() == insumo.id());
            if (activo) insumos.add(insumo);
            else omitidos.add(insumo.nombre());
        }

        ConfiguracionCopiada depurada =
            new ConfiguracionCopiada(original.tipo(), jabon, original.litrosJabon(), insumos);
        return new Resultado(depurada, omitidos);
    }
}
