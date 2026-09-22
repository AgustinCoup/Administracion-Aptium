package com.example.features.lavadero.controller.helpers;

import com.example.features.lavadero.model.JabonCatalogo;
import com.example.features.lavadero.model.OrigenJabon;
import com.example.features.lavadero.model.TipoLavado;

import java.util.Map;
import java.util.Optional;

/**
 * Qué jabón corresponde poner en una card cuando cambia su tipo de lavado.
 *
 * <p><b>Toda la regla vive acá</b>, en una clase plana y sin Swing: la card sabe mostrar y guardar
 * el {@link OrigenJabon}, y el controller sabe cablear el evento, pero ninguno de los dos decide.
 * Es el mismo patrón que {@code ConstructorVistaCiclos} o {@code SincronizadorVolumenFinal} — la
 * lógica de negocio embebida en un {@code JPanel} no se puede testear.</p>
 *
 * <p><b>La regla, en positivo: una elección a mano siempre pesa más que la automática.</b> Escrita
 * al revés ("pisar salvo que…") cualquier caso nuevo hereda por omisión un pisado que no le
 * corresponde, y lo que se pierde es trabajo del operador sin que él haya tocado nada.</p>
 */
public final class SelectorJabonAutomatico {

    private SelectorJabonAutomatico() {
    }

    /**
     * El jabón que hay que cargar, o vacío cuando <b>no hay que tocar nada</b>.
     *
     * <table border="1">
     *   <caption>Los cinco casos</caption>
     *   <tr><th>Situación</th><th>Resultado</th></tr>
     *   <tr><td>{@code tipoNuevo == null} (se deseleccionó el tipo)</td><td>vacío</td></tr>
     *   <tr><td>{@code origen == MANUAL} y ya hay jabón</td><td>vacío — la elección a mano manda</td></tr>
     *   <tr><td>no hay default para ese tipo</td><td>vacío</td></tr>
     *   <tr><td>el default existe pero está dado de baja</td><td>vacío</td></tr>
     *   <tr><td>el resto ({@code jabonActual == null} <b>o</b> {@code origen == AUTO})</td><td>el default del tipo</td></tr>
     * </table>
     *
     * <p>Los dos "vacío" del medio son la misma decisión escrita dos veces: sin default configurado
     * —o con el default dado de baja desde Ajustes— la carga automática no tiene nada que ofrecer,
     * y dejar el combo como está es más honesto que vaciarlo. Que el {@code JOIN} del DAO
     * <b>no</b> filtre por {@code activo} es lo que permite tomar esta decisión acá en vez de que
     * la tome la consulta: Ajustes necesita poder mostrar "el default de Sucio es un jabón de
     * baja" para que alguien lo arregle.</p>
     *
     * @param tipoNuevo   el tipo que quedó elegido en la card; {@code null} si se deseleccionó
     * @param jabonActual el jabón que la card muestra ahora; {@code null} si el combo está vacío
     * @param origen      quién puso {@code jabonActual}
     * @param defaults    jabón por defecto de cada tipo, tal como vino de la base (puede incluir
     *                    jabones dados de baja)
     */
    public static Optional<JabonCatalogo> alCambiarTipo(TipoLavado tipoNuevo,
                                                        JabonCatalogo jabonActual,
                                                        OrigenJabon origen,
                                                        Map<TipoLavado, JabonCatalogo> defaults) {
        if (tipoNuevo == null) return Optional.empty();
        if (origen == OrigenJabon.MANUAL && jabonActual != null) return Optional.empty();
        if (defaults == null) return Optional.empty();

        JabonCatalogo porDefecto = defaults.get(tipoNuevo);
        if (porDefecto == null || !porDefecto.isActivo()) return Optional.empty();
        return Optional.of(porDefecto);
    }
}
