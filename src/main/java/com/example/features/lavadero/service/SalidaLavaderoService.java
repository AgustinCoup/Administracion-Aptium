package com.example.features.lavadero.service;

import com.example.common.exception.ValidationException;
import com.example.features.lavadero.dao.SalidaLavaderoDAO;
import com.example.features.lavadero.dao.derivadores.DerivadorSalidas;
import com.example.features.lavadero.model.AccionSalida;
import com.example.features.lavadero.model.ElementoLavadoPendiente;
import com.example.features.lavadero.model.MarcaListo;
import com.example.features.lavadero.model.SalidaLista;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Capa de validación sobre {@link SalidaLavaderoDAO}. Sólo valida y delega, igual que
 * {@code CicloLavaderoService}: cero JDBC acá.
 *
 * <p>Elige el {@link DerivadorSalidas} según la {@link AccionSalida} pedida. El registro se arma
 * una vez en el constructor y se valida ahí mismo: una acción sin derivador, o dos derivadores
 * para la misma acción, es un bug de cableado que tiene que explotar al levantar la app, no
 * cuando el operador hace clic.</p>
 */
public class SalidaLavaderoService {

    private final SalidaLavaderoDAO dao;
    private final Map<AccionSalida, DerivadorSalidas> derivadoresPorAccion;

    public SalidaLavaderoService(SalidaLavaderoDAO dao, List<DerivadorSalidas> derivadores) {
        if (dao == null) throw new IllegalArgumentException("SalidaLavaderoDAO no puede ser nulo");
        if (derivadores == null) throw new IllegalArgumentException("La lista de derivadores no puede ser nula");

        this.dao = dao;
        this.derivadoresPorAccion = new EnumMap<>(AccionSalida.class);
        for (DerivadorSalidas derivador : derivadores) {
            if (derivadoresPorAccion.put(derivador.accion(), derivador) != null) {
                throw new IllegalArgumentException(
                    "Hay más de un derivador registrado para la acción " + derivador.accion());
            }
        }
        for (AccionSalida accion : AccionSalida.values()) {
            if (!derivadoresPorAccion.containsKey(accion)) {
                throw new IllegalArgumentException(
                    "Falta un derivador para la acción " + accion);
            }
        }
    }

    public List<ElementoLavadoPendiente> obtenerLavadosPendientesDeListo() {
        return dao.obtenerLavadosPendientesDeListo();
    }

    public List<SalidaLista> obtenerListasSinDestino() {
        return dao.obtenerListasSinDestino();
    }

    /**
     * Marca como Listo la selección entera. Una sola firma para los dos modos de la pantalla:
     * marcado parcial es una lista de una {@link MarcaListo}; marcado masivo trae
     * {@code item.cantidadPendiente()} en cada una. El service no distingue cuál de los dos fue.
     */
    public void marcarListo(List<MarcaListo> marcas) {
        ValidationException.Builder v = ValidationException.builder();
        v.addErrorIf(marcas == null || marcas.isEmpty(), "No hay ninguna cantidad para marcar como Listo.");

        if (marcas != null) {
            Set<Integer> elementosVistos = new HashSet<>();
            for (MarcaListo marca : marcas) {
                v.addErrorIf(marca.item() == null, "Hay una marca sin elemento asociado.");
                if (marca.item() == null) continue;

                v.addErrorIf(marca.cantidad() <= 0,
                    "La cantidad a marcar como Listo de " + describir(marca.item())
                    + " tiene que ser mayor que cero.");
                v.addErrorIf(marca.cantidad() > marca.item().cantidadPendiente(),
                    describir(marca.item()) + " no tiene " + marca.cantidad()
                    + " disponibles (quedan " + marca.item().cantidadPendiente() + ").");
                v.addErrorIf(!elementosVistos.add(marca.item().elementoCicloId()),
                    describir(marca.item()) + " está repetido en la selección.");
            }
        }

        v.throwIfHasErrors();
        dao.marcarListo(marcas);
    }

    /**
     * Devuelve a Lavado la selección entera. Una sola firma para el botón y para el arrastre:
     * los dos mandan la tanda completa y el todo-o-nada lo garantiza la transacción del DAO,
     * no un bucle de llamados de a uno.
     */
    public void volverALavado(List<SalidaLista> salidas) {
        ValidationException.Builder v = ValidationException.builder();
        v.addErrorIf(salidas == null || salidas.isEmpty(), "No hay ninguna salida seleccionada.");

        if (salidas != null) {
            Set<Integer> salidasVistas = new HashSet<>();
            for (SalidaLista salida : salidas) {
                v.addErrorIf(salida == null, "Hay una salida sin datos en la selección.");
                if (salida == null) continue;

                v.addErrorIf(salida.salidaId() <= 0, "ID de salida inválido.");
                v.addErrorIf(!salidasVistas.add(salida.salidaId()),
                    describir(salida) + " está repetida en la selección.");
            }
        }

        v.throwIfHasErrors();
        dao.volverALavado(salidas.stream().map(SalidaLista::salidaId).toList());
    }

    /** Deriva la selección al destino que le corresponde a {@code accion}, en una sola transacción. */
    public void derivar(AccionSalida accion, List<SalidaLista> seleccion) {
        ValidationException.Builder v = ValidationException.builder();
        v.addErrorIf(accion == null, "Debe indicar qué hacer con las salidas seleccionadas.");
        v.addErrorIf(seleccion == null || seleccion.isEmpty(), "No hay ninguna salida seleccionada.");
        if (seleccion != null) {
            for (SalidaLista salida : seleccion) {
                v.addErrorIf(salida.cantidad() <= 0,
                    "La cantidad de " + salida.elementoNombre() + " (" + salida.clienteNombre()
                    + ") tiene que ser mayor que cero.");
            }
        }
        v.throwIfHasErrors();

        dao.derivar(derivadoresPorAccion.get(accion), seleccion);
    }

    private String describir(ElementoLavadoPendiente item) {
        return item.elementoNombre() + " (" + item.clienteNombre()
             + ", lavarropas " + item.lavarropasNumero() + ")";
    }

    private String describir(SalidaLista salida) {
        return salida.elementoNombre() + " (" + salida.clienteNombre()
             + ", lavarropas " + salida.lavarropasNumero() + ")";
    }
}
