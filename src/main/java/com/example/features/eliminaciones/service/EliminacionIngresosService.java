package com.example.features.eliminaciones.service;

import com.example.common.constants.Constantes;
import com.example.common.constants.Constantes.Mensajes;
import com.example.common.eliminacion.IngresoAEliminar;
import com.example.common.eliminacion.ModuloIngreso;
import com.example.common.eliminacion.PuestoDeTrabajo;
import com.example.common.eliminacion.ResumenEliminacion;
import com.example.common.eliminacion.ResumenEquipo;
import com.example.common.eliminacion.ResumenIngresoLavadero;
import com.example.common.exception.ValidationException;
import com.example.features.equipos.ortopedias.dao.EliminadorEquipoOrtopedia;
import com.example.features.equipos.otros.dao.EliminadorEquipoOtros;
import com.example.features.lavadero.dao.EliminadorIngresoLavadero;
import com.example.features.seguridad.service.PasswordEliminacionService;

import java.util.Objects;

/**
 * Eliminar un ingreso completo: une la password con los tres eliminadores.
 *
 * <p>Sin JDBC: valida y despacha. Todo corre fuera del EDT (la verificación de la password es
 * PBKDF2 y tarda a propósito).</p>
 *
 * <p><b>La password se verifica antes, y fuera de la transacción del borrado.</b> No hace falta
 * atomicidad: verificar no escribe nada, y si la password es incorrecta no hay nada que revertir.
 * Meterla adentro tendría un costo real: mantendría abierta la transacción (y sus locks) durante
 * los cientos de ms de PBKDF2. El orden es: motivo → password → borrado. El motivo va primero
 * porque es barato y evita hashear para una solicitud que igual se iba a rechazar.</p>
 *
 * <p><b>Los {@code char[]} son de quien los creó.</b> Este service no los limpia ni los copia a un
 * {@code String}.</p>
 */
public class EliminacionIngresosService {

    private final EliminadorEquipoOrtopedia eliminadorOrtopedia;
    private final EliminadorEquipoOtros eliminadorOtros;
    private final EliminadorIngresoLavadero eliminadorLavadero;
    private final PasswordEliminacionService passwordService;

    public EliminacionIngresosService(EliminadorEquipoOrtopedia eliminadorOrtopedia,
                                      EliminadorEquipoOtros eliminadorOtros,
                                      EliminadorIngresoLavadero eliminadorLavadero,
                                      PasswordEliminacionService passwordService) {
        if (eliminadorOrtopedia == null || eliminadorOtros == null || eliminadorLavadero == null
            || passwordService == null) {
            throw new IllegalArgumentException("EliminacionIngresosService requiere dependencias no nulas");
        }
        this.eliminadorOrtopedia = eliminadorOrtopedia;
        this.eliminadorOtros = eliminadorOtros;
        this.eliminadorLavadero = eliminadorLavadero;
        this.passwordService = passwordService;
    }

    /** Lo que desaparece, lo que lo bloquea y el token de guarda, leído de la base. */
    public ResumenEliminacion resumir(IngresoAEliminar ingreso) {
        Objects.requireNonNull(ingreso, "ingreso");
        return switch (ingreso.modulo()) {
            case ORTOPEDIA -> eliminadorOrtopedia.resumir(ingreso.id());
            case OTROS -> eliminadorOtros.resumir(ingreso.id());
            case LAVADERO -> eliminadorLavadero.resumir(ingreso.id());
        };
    }

    /** ¿Sigue vigente la password inicial? El diálogo avisa mientras lo esté. */
    public boolean passwordEsInicial() {
        return passwordService.esInicial();
    }

    /**
     * @param password la que tipeó el operador; <b>no se limpia acá</b>
     * @throws ValidationException                                si el motivo falta o pasa el máximo
     * @throws com.example.common.exception.PasswordIncorrectaException si la password no coincide
     * @throws com.example.common.exception.ConflictoConcurrenciaException si el ingreso cambió desde el resumen
     * @throws com.example.common.eliminacion.EliminacionBloqueadaException si hay un lote o ciclo en curso
     */
    public void eliminar(SolicitudEliminacion solicitud, char[] password) {
        Objects.requireNonNull(solicitud, "solicitud");
        String motivo = validarMotivo(solicitud.motivo());
        passwordService.verificar(password);

        String puesto = PuestoDeTrabajo.actual();
        ResumenEliminacion resumen = solicitud.resumen();
        if (resumen instanceof ResumenEquipo equipo) {
            eliminarEquipo(equipo, motivo, puesto);
        } else if (resumen instanceof ResumenIngresoLavadero ingreso) {
            eliminadorLavadero.eliminar(ingreso.ingreso().id(), ingreso.estado(), ingreso.versionesDerivados(),
                motivo, puesto);
        }
    }

    private void eliminarEquipo(ResumenEquipo r, String motivo, String puesto) {
        int id = r.ingreso().id();
        if (r.ingreso().modulo() == ModuloIngreso.ORTOPEDIA) {
            eliminadorOrtopedia.eliminar(id, r.version(), motivo, puesto);
        } else {
            eliminadorOtros.eliminar(id, r.version(), motivo, puesto);
        }
    }

    private static String validarMotivo(String motivo) {
        String limpio = motivo == null ? "" : motivo.strip();
        int maximo = Constantes.Eliminacion.MOTIVO_MAX_LARGO;
        ValidationException.builder()
            .addErrorIf(limpio.isEmpty(), Mensajes.MOTIVO_ELIMINACION_OBLIGATORIO)
            .addErrorIf(limpio.length() > maximo, String.format(Mensajes.MOTIVO_ELIMINACION_LARGO, maximo))
            .throwIfHasErrors();
        return limpio;
    }
}
